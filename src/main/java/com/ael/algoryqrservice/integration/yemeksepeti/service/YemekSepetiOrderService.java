package com.ael.algoryqrservice.integration.yemeksepeti.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.integration.yemeksepeti.client.YemekSepetiClient;
import com.ael.algoryqrservice.integration.yemeksepeti.config.YemekSepetiProperties;
import com.ael.algoryqrservice.integration.yemeksepeti.mapper.YemekSepetiPayloadMapper;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiConnection;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiConnectionStatus;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiOrder;
import com.ael.algoryqrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.ael.algoryqrservice.integration.yemeksepeti.repository.YemekSepetiConnectionRepository;
import com.ael.algoryqrservice.integration.yemeksepeti.repository.YemekSepetiOrderRepository;
import com.ael.algoryqrservice.integration.yemeksepeti.repository.YemekSepetiOrderSpecifications;
import com.ael.algoryqrservice.print.service.PrintOrderEnqueueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class YemekSepetiOrderService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final YemekSepetiConnectionService connectionService;
    private final YemekSepetiConnectionRepository connectionRepository;
    private final YemekSepetiOrderRepository orderRepository;
    private final YemekSepetiClient yemekSepetiClient;
    private final YemekSepetiPayloadMapper payloadMapper;
    private final YemekSepetiProperties properties;
    private final ObjectMapper objectMapper;
    private final PrintOrderEnqueueService printOrderEnqueueService;

    @Transactional(readOnly = true)
    public YemekSepetiDtos.OrderPageResponse listOrders(
            String status,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        YemekSepetiConnection connection = connectionService.requireConnected();
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(0, page);
        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);
        Page<YemekSepetiOrder> result = orderRepository.findAll(
                YemekSepetiOrderSpecifications.forConnectionListed(
                        connection.getId(),
                        hasText(status) ? status.trim() : null,
                        fromDt,
                        toDt
                ),
                PageRequest.of(safePage, safeSize)
        );
        return YemekSepetiDtos.OrderPageResponse.builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public YemekSepetiDtos.OrderResponse accept(Long orderId) {
        return applyAction(orderId, yemekSepetiClient::acceptOrder, "Accepted");
    }

    @Transactional
    public YemekSepetiDtos.OrderResponse reject(Long orderId) {
        return applyAction(orderId, yemekSepetiClient::rejectOrder, "Rejected");
    }

    @Transactional
    public YemekSepetiDtos.OrderResponse cancel(Long orderId) {
        return applyAction(orderId, yemekSepetiClient::cancelOrder, "Cancelled");
    }

    @Transactional
    public YemekSepetiDtos.OrderResponse markReady(Long orderId) {
        return applyAction(orderId, yemekSepetiClient::markReady, "READY_FOR_PICKUP");
    }

    @Transactional
    public void ingestWebhook(String apiKey, String authorization, JsonNode payload) {
        List<JsonNode> nodes = payloadMapper.toOrderNodes(payload);
        if (nodes.isEmpty()) {
            throw new BadRequestException("Sipariş yükü okunamadı");
        }
        for (JsonNode node : nodes) {
            YemekSepetiConnection connection = resolveConnection(node);
            if (connection == null) {
                continue;
            }
            assertWebhookSecret(connection, apiKey, authorization);
            upsertOrder(connection, node);
        }
    }

    @Transactional
    public int reconcileConnected() {
        if (!properties.isPollEnabled()) {
            return 0;
        }
        Instant end = Instant.now();
        Instant start = end.minusSeconds((long) properties.getPollLookbackHours() * 3600L);
        int upserted = 0;
        for (YemekSepetiConnection connection : connectionRepository.findByStatus(YemekSepetiConnectionStatus.CONNECTED)) {
            try {
                upserted += syncConnection(connection, start, end);
                connection.setLastSyncedAt(LocalDateTime.now());
                connection.setLastError(null);
                connectionRepository.save(connection);
            } catch (Exception exception) {
                connection.setStatus(YemekSepetiConnectionStatus.ERROR);
                connection.setLastError(exception.getMessage());
                connectionRepository.save(connection);
                log.warn("Yemeksepeti sipariş senkronu başarısız connectionId={}", connection.getId());
            }
        }
        return upserted;
    }

    @Transactional
    public YemekSepetiDtos.SyncOrdersResponse syncOrders(LocalDate from, LocalDate to) {
        YemekSepetiConnection connection = connectionService.requireConnected();
        InstantRange range = resolveSyncRange(from, to);
        int upserted = syncConnection(connection, range.start(), range.end());
        connection.setLastSyncedAt(LocalDateTime.now());
        connection.setLastError(null);
        connectionRepository.save(connection);
        return YemekSepetiDtos.SyncOrdersResponse.builder()
                .upserted(upserted)
                .lookbackHours(properties.getPollLookbackHours())
                .from(from)
                .to(to)
                .build();
    }

    public YemekSepetiOrder upsertOrder(YemekSepetiConnection connection, JsonNode node) {
        String externalId = payloadMapper.externalOrderId(node);
        if (!hasText(externalId)) {
            return null;
        }
        var existing = orderRepository.findByConnectionIdAndExternalOrderId(connection.getId(), externalId);
        boolean isNew = existing.isEmpty();
        YemekSepetiOrder order = existing.orElseGet(() -> YemekSepetiOrder.builder()
                .connectionId(connection.getId())
                .externalOrderId(externalId)
                .build());
        order.setPackageStatus(payloadMapper.packageStatus(node));
        order.setTotalAmount(payloadMapper.totalAmount(node));
        order.setCurrency(payloadMapper.currency(node));
        order.setCustomerName(payloadMapper.customerName(node));
        order.setCustomerPhone(payloadMapper.customerPhone(node));
        order.setDeliveryAddress(payloadMapper.deliveryAddress(node));
        order.setNote(payloadMapper.note(node));
        order.setPackageCreatedAt(payloadMapper.packageCreatedAt(node));
        List<YemekSepetiDtos.OrderItemResponse> items = payloadMapper.toOrderItems(node);
        order.setItemsJson(writeJson(items));
        order.setRawPayload(node.toString());
        if (!hasText(connection.getVendorName())) {
            connection.setVendorName(payloadMapper.vendorName(node));
            connectionRepository.save(connection);
        }
        YemekSepetiOrder saved = orderRepository.save(order);
        if (isNew) {
            printOrderEnqueueService.enqueueYemekSepetiOrder(connection.getUserId(), null, saved, items);
        }
        return saved;
    }

    private InstantRange resolveSyncRange(LocalDate from, LocalDate to) {
        if (from != null || to != null) {
            LocalDate endDate = to != null ? to : LocalDate.now(ZONE);
            LocalDate startDate = from != null ? from : endDate.minusDays(29);
            if (startDate.isAfter(endDate)) {
                throw new BadRequestException("Başlangıç tarihi bitiş tarihinden sonra olamaz");
            }
            return new InstantRange(
                    startDate.atStartOfDay(ZONE).toInstant(),
                    endDate.plusDays(1).atStartOfDay(ZONE).toInstant().minusMillis(1)
            );
        }
        Instant end = Instant.now();
        return new InstantRange(end.minusSeconds((long) properties.getPollLookbackHours() * 3600L), end);
    }

    private record InstantRange(Instant start, Instant end) {
    }

    private int syncConnection(YemekSepetiConnection connection, Instant start, Instant end) {
        int upserted = 0;
        for (JsonNode node : yemekSepetiClient.listAllOrders(connectionService.decrypt(connection), start, end)) {
            if (upsertOrder(connection, node) != null) {
                upserted++;
            }
        }
        return upserted;
    }

    private YemekSepetiDtos.OrderResponse applyAction(
            Long orderId,
            OrderAction action,
            String localStatus
    ) {
        YemekSepetiConnection connection = connectionService.requireConnected();
        YemekSepetiOrder order = orderRepository.findByIdAndConnectionId(orderId, connection.getId())
                .orElseThrow(() -> new NotFoundException("Yemeksepeti siparişi bulunamadı"));
        action.apply(connectionService.decrypt(connection), order.getExternalOrderId());
        order.setPackageStatus(localStatus);
        return toResponse(orderRepository.save(order));
    }

    private YemekSepetiConnection resolveConnection(JsonNode node) {
        String vendorId = payloadMapper.vendorId(node);
        if (hasText(vendorId)) {
            List<YemekSepetiConnection> matches = connectionRepository.findByVendorId(vendorId);
            if (!matches.isEmpty()) {
                return matches.getFirst();
            }
        }
        String chainId = payloadMapper.chainId(node);
        if (hasText(chainId)) {
            List<YemekSepetiConnection> matches = connectionRepository.findByChainId(chainId);
            if (!matches.isEmpty()) {
                return matches.getFirst();
            }
        }
        return null;
    }

    private void assertWebhookSecret(YemekSepetiConnection connection, String apiKey, String authorization) {
        String expected = connectionService.decrypt(connection).getWebhookSecret();
        if (!hasText(expected)) {
            return;
        }
        if (expected.equals(apiKey) || matchesAuthorization(expected, authorization)) {
            return;
        }
        throw new UnauthorizedException("Webhook anahtarı geçersiz");
    }

    private boolean matchesAuthorization(String expected, String authorization) {
        if (!hasText(authorization)) {
            return false;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return expected.equals(value.substring(7).trim());
        }
        if (value.regionMatches(true, 0, "Basic ", 0, 6)) {
            return expected.equals(value.substring(6).trim());
        }
        return expected.equals(value);
    }

    private YemekSepetiDtos.OrderResponse toResponse(YemekSepetiOrder order) {
        JsonNode rawPayload = readRawPayload(order.getRawPayload());
        List<YemekSepetiDtos.OrderItemResponse> items = readItems(order.getItemsJson());
        if (items.isEmpty() && rawPayload != null) {
            items = payloadMapper.toOrderItems(rawPayload);
        }
        return YemekSepetiDtos.OrderResponse.builder()
                .id(order.getId())
                .externalOrderId(order.getExternalOrderId())
                .orderNumber(rawPayload != null ? payloadMapper.orderNumber(rawPayload) : null)
                .deliveryType(rawPayload != null ? payloadMapper.deliveryType(rawPayload) : null)
                .paymentMethod(rawPayload != null ? payloadMapper.paymentMethod(rawPayload) : null)
                .packageStatus(order.getPackageStatus())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .deliveryAddress(order.getDeliveryAddress())
                .note(order.getNote())
                .packageCreatedAt(order.getPackageCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(items)
                .build();
    }

    private JsonNode readRawPayload(String rawPayload) {
        if (!hasText(rawPayload)) {
            return null;
        }
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private List<YemekSepetiDtos.OrderItemResponse> readItems(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface OrderAction {
        void apply(YemekSepetiDtos.Credentials credentials, String orderId);
    }
}
