package com.ael.algoryqrservice.integration.ubereats.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.config.UberEatsProperties;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsOrder;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsConnectionRepository;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsOrderRepository;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsOrderSpecifications;
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
public class UberEatsOrderService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final UberEatsConnectionService connectionService;
    private final UberEatsConnectionRepository connectionRepository;
    private final UberEatsOrderRepository orderRepository;
    private final UberEatsClient uberEatsClient;
    private final UberEatsPayloadMapper payloadMapper;
    private final UberEatsProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UberEatsDtos.OrderPageResponse listOrders(
            String status,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        UberEatsConnection connection = connectionService.requireConnected();
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(0, page);
        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);
        Page<UberEatsOrder> result = orderRepository.findAll(
                UberEatsOrderSpecifications.forConnectionListed(
                        connection.getId(),
                        hasText(status) ? status.trim() : null,
                        fromDt,
                        toDt
                ),
                PageRequest.of(safePage, safeSize)
        );
        return UberEatsDtos.OrderPageResponse.builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public UberEatsDtos.OrderResponse getOrder(Long orderId) {
        UberEatsConnection connection = connectionService.requireConnected();
        UberEatsOrder order = orderRepository.findByIdAndConnectionId(orderId, connection.getId())
                .orElseThrow(() -> new NotFoundException("Uber Eats siparişi bulunamadı"));
        return toResponse(order);
    }

    @Transactional
    public UberEatsDtos.OrderResponse accept(Long orderId) {
        return applyAction(orderId, uberEatsClient::acceptOrder, "Accepted");
    }

    @Transactional
    public UberEatsDtos.OrderResponse reject(Long orderId) {
        return applyAction(orderId, uberEatsClient::rejectOrder, "Rejected");
    }

    @Transactional
    public UberEatsDtos.OrderResponse cancel(Long orderId) {
        return applyAction(orderId, uberEatsClient::cancelOrder, "Cancelled");
    }

    @Transactional
    public UberEatsDtos.OrderResponse markReady(Long orderId) {
        return applyAction(orderId, uberEatsClient::markReady, "Prepared");
    }

    @Transactional
    public void ingestWebhook(String apiKey, JsonNode payload) {
        assertWebhookKey(apiKey);
        List<JsonNode> nodes = payloadMapper.toOrderNodes(payload);
        if (nodes.isEmpty()) {
            throw new BadRequestException("Sipariş yükü okunamadı");
        }
        for (JsonNode node : nodes) {
            UberEatsConnection connection = resolveConnection(node);
            if (connection == null) {
                continue;
            }
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
        for (UberEatsConnection connection : connectionRepository.findByStatus(UberEatsConnectionStatus.CONNECTED)) {
            try {
                upserted += syncConnection(connection, start, end);
                connection.setLastSyncedAt(LocalDateTime.now());
                connection.setLastError(null);
                connectionRepository.save(connection);
            } catch (Exception exception) {
                connection.setStatus(UberEatsConnectionStatus.ERROR);
                connection.setLastError(exception.getMessage());
                connectionRepository.save(connection);
                log.warn("Uber Eats sipariş senkronu başarısız connectionId={}", connection.getId());
            }
        }
        return upserted;
    }

    @Transactional(readOnly = true)
    public int syncLookbackHours() {
        return properties.getPollLookbackHours();
    }

    @Transactional
    public UberEatsDtos.SyncOrdersResponse syncOrders(LocalDate from, LocalDate to) {
        UberEatsConnection connection = connectionService.requireConnected();
        InstantRange range = resolveSyncRange(from, to);
        int upserted = syncConnection(connection, range.start(), range.end());
        connection.setLastSyncedAt(LocalDateTime.now());
        connection.setLastError(null);
        connectionRepository.save(connection);
        return UberEatsDtos.SyncOrdersResponse.builder()
                .upserted(upserted)
                .lookbackHours(properties.getPollLookbackHours())
                .from(from)
                .to(to)
                .build();
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

    private int syncConnection(UberEatsConnection connection, Instant start, Instant end) {
        int upserted = 0;
        for (JsonNode node : uberEatsClient.listAllOrders(connectionService.decrypt(connection), start, end)) {
            if (upsertOrder(connection, node) != null) {
                upserted++;
            }
        }
        return upserted;
    }

    public UberEatsOrder upsertOrder(UberEatsConnection connection, JsonNode node) {
        String externalId = payloadMapper.externalOrderId(node);
        if (!hasText(externalId)) {
            return null;
        }
        UberEatsOrder order = orderRepository
                .findByConnectionIdAndExternalOrderId(connection.getId(), externalId)
                .orElseGet(() -> UberEatsOrder.builder()
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
        order.setItemsJson(writeJson(payloadMapper.toOrderItems(node)));
        order.setRawPayload(node.toString());
        return orderRepository.save(order);
    }

    private UberEatsDtos.OrderResponse applyAction(
            Long orderId,
            OrderAction action,
            String localStatus
    ) {
        UberEatsConnection connection = connectionService.requireConnected();
        UberEatsOrder order = orderRepository.findByIdAndConnectionId(orderId, connection.getId())
                .orElseThrow(() -> new NotFoundException("Uber Eats siparişi bulunamadı"));
        action.apply(connectionService.decrypt(connection), order.getExternalOrderId());
        order.setPackageStatus(localStatus);
        return toResponse(orderRepository.save(order));
    }

    private UberEatsConnection resolveConnection(JsonNode node) {
        String restaurantId = payloadMapper.restaurantId(node);
        if (hasText(restaurantId)) {
            List<UberEatsConnection> matches = connectionRepository.findByRestaurantId(restaurantId);
            if (!matches.isEmpty()) {
                return matches.getFirst();
            }
        }
        String sellerId = payloadMapper.sellerId(node);
        if (hasText(sellerId)) {
            List<UberEatsConnection> matches = connectionRepository.findBySellerId(sellerId);
            if (!matches.isEmpty()) {
                return matches.getFirst();
            }
        }
        return null;
    }

    private void assertWebhookKey(String apiKey) {
        String expected = properties.getWebhookApiKey();
        if (!hasText(expected)) {
            return;
        }
        if (!expected.equals(apiKey)) {
            throw new UnauthorizedException("Webhook anahtarı geçersiz");
        }
    }

    private UberEatsDtos.OrderResponse toResponse(UberEatsOrder order) {
        JsonNode rawPayload = readRawPayload(order.getRawPayload());
        List<UberEatsDtos.OrderItemResponse> items = readItems(order.getItemsJson());
        if (items.isEmpty() && rawPayload != null) {
            items = payloadMapper.toOrderItems(rawPayload);
        } else if (rawPayload != null && items.stream().allMatch(this::itemMissingDetail)) {
            items = payloadMapper.toOrderItems(rawPayload);
        }

        String orderNumber = rawPayload != null ? payloadMapper.orderNumber(rawPayload) : null;
        String deliveryType = rawPayload != null ? payloadMapper.deliveryType(rawPayload) : null;
        String paymentMethod = rawPayload != null ? payloadMapper.paymentMethod(rawPayload) : null;

        return UberEatsDtos.OrderResponse.builder()
                .id(order.getId())
                .externalOrderId(order.getExternalOrderId())
                .orderNumber(orderNumber)
                .deliveryType(deliveryType)
                .paymentMethod(paymentMethod)
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

    private boolean itemMissingDetail(UberEatsDtos.OrderItemResponse item) {
        return item.getProductName() == null
                || item.getProductName().isBlank()
                || item.getDetail() == null
                || item.getDetail().isBlank();
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

    private List<UberEatsDtos.OrderItemResponse> readItems(String json) {
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
        void apply(UberEatsDtos.Credentials credentials, String orderId);
    }
}
