package com.ael.algoryqrservice.integration.trendyolgo.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.integration.trendyolgo.client.TrendyolGoClient;
import com.ael.algoryqrservice.integration.trendyolgo.config.TrendyolGoProperties;
import com.ael.algoryqrservice.integration.trendyolgo.mapper.TrendyolGoPayloadMapper;
import com.ael.algoryqrservice.integration.trendyolgo.model.TrendyolGoConnection;
import com.ael.algoryqrservice.integration.trendyolgo.model.TrendyolGoConnectionStatus;
import com.ael.algoryqrservice.integration.trendyolgo.model.TrendyolGoOrder;
import com.ael.algoryqrservice.integration.trendyolgo.model.dto.TrendyolGoDtos;
import com.ael.algoryqrservice.integration.trendyolgo.repository.TrendyolGoConnectionRepository;
import com.ael.algoryqrservice.integration.trendyolgo.repository.TrendyolGoOrderRepository;
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
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendyolGoOrderService {

    private final TrendyolGoConnectionService connectionService;
    private final TrendyolGoConnectionRepository connectionRepository;
    private final TrendyolGoOrderRepository orderRepository;
    private final TrendyolGoClient trendyolGoClient;
    private final TrendyolGoPayloadMapper payloadMapper;
    private final TrendyolGoProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TrendyolGoDtos.OrderPageResponse listOrders(Long branchId, String status, int page, int size) {
        TrendyolGoConnection connection = connectionService.requireConnected(branchId);
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(0, page);
        Page<TrendyolGoOrder> result = hasText(status)
                ? orderRepository.findByConnectionIdAndPackageStatusIgnoreCaseOrderByPackageCreatedAtDesc(
                connection.getId(), status.trim(), PageRequest.of(safePage, safeSize))
                : orderRepository.findByConnectionIdOrderByPackageCreatedAtDesc(
                connection.getId(), PageRequest.of(safePage, safeSize));
        return TrendyolGoDtos.OrderPageResponse.builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public TrendyolGoDtos.OrderResponse getOrder(Long branchId, Long orderId) {
        TrendyolGoConnection connection = connectionService.requireConnected(branchId);
        TrendyolGoOrder order = orderRepository.findByIdAndConnectionId(orderId, connection.getId())
                .orElseThrow(() -> new NotFoundException("TGO siparişi bulunamadı"));
        return toResponse(order);
    }

    @Transactional
    public TrendyolGoDtos.OrderResponse accept(Long branchId, Long orderId) {
        return applyAction(branchId, orderId, trendyolGoClient::acceptOrder, "Accepted");
    }

    @Transactional
    public TrendyolGoDtos.OrderResponse reject(Long branchId, Long orderId) {
        return applyAction(branchId, orderId, trendyolGoClient::rejectOrder, "Rejected");
    }

    @Transactional
    public TrendyolGoDtos.OrderResponse cancel(Long branchId, Long orderId) {
        return applyAction(branchId, orderId, trendyolGoClient::cancelOrder, "Cancelled");
    }

    @Transactional
    public TrendyolGoDtos.OrderResponse markReady(Long branchId, Long orderId) {
        return applyAction(branchId, orderId, trendyolGoClient::markReady, "Prepared");
    }

    @Transactional
    public void ingestWebhook(String apiKey, JsonNode payload) {
        assertWebhookKey(apiKey);
        List<JsonNode> nodes = payloadMapper.toOrderNodes(payload);
        if (nodes.isEmpty()) {
            throw new BadRequestException("Sipariş yükü okunamadı");
        }
        for (JsonNode node : nodes) {
            TrendyolGoConnection connection = resolveConnection(node);
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
        Instant start = end.minusSeconds(properties.getPollLookbackHours() * 3600L);
        int upserted = 0;
        for (TrendyolGoConnection connection : connectionRepository.findByStatus(TrendyolGoConnectionStatus.CONNECTED)) {
            try {
                JsonNode payload = trendyolGoClient.listOrders(connectionService.decrypt(connection), start, end);
                for (JsonNode node : payloadMapper.toOrderNodes(payload)) {
                    if (upsertOrder(connection, node) != null) {
                        upserted++;
                    }
                }
                connection.setLastSyncedAt(LocalDateTime.now());
                connection.setLastError(null);
                connectionRepository.save(connection);
            } catch (Exception exception) {
                connection.setStatus(TrendyolGoConnectionStatus.ERROR);
                connection.setLastError(exception.getMessage());
                connectionRepository.save(connection);
                log.warn("TGO sipariş senkronu başarısız connectionId={}", connection.getId());
            }
        }
        return upserted;
    }

    public TrendyolGoOrder upsertOrder(TrendyolGoConnection connection, JsonNode node) {
        String externalId = payloadMapper.externalOrderId(node);
        if (!hasText(externalId)) {
            return null;
        }
        TrendyolGoOrder order = orderRepository
                .findByConnectionIdAndExternalOrderId(connection.getId(), externalId)
                .orElseGet(() -> TrendyolGoOrder.builder()
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

    private TrendyolGoDtos.OrderResponse applyAction(
            Long branchId,
            Long orderId,
            OrderAction action,
            String localStatus
    ) {
        TrendyolGoConnection connection = connectionService.requireConnected(branchId);
        TrendyolGoOrder order = orderRepository.findByIdAndConnectionId(orderId, connection.getId())
                .orElseThrow(() -> new NotFoundException("TGO siparişi bulunamadı"));
        action.apply(connectionService.decrypt(connection), order.getExternalOrderId());
        order.setPackageStatus(localStatus);
        return toResponse(orderRepository.save(order));
    }

    private TrendyolGoConnection resolveConnection(JsonNode node) {
        String restaurantId = payloadMapper.restaurantId(node);
        if (hasText(restaurantId)) {
            List<TrendyolGoConnection> matches = connectionRepository.findByRestaurantId(restaurantId);
            if (!matches.isEmpty()) {
                return matches.getFirst();
            }
        }
        String sellerId = payloadMapper.sellerId(node);
        if (hasText(sellerId)) {
            List<TrendyolGoConnection> matches = connectionRepository.findBySellerId(sellerId);
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

    private TrendyolGoDtos.OrderResponse toResponse(TrendyolGoOrder order) {
        return TrendyolGoDtos.OrderResponse.builder()
                .id(order.getId())
                .externalOrderId(order.getExternalOrderId())
                .packageStatus(order.getPackageStatus())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .deliveryAddress(order.getDeliveryAddress())
                .note(order.getNote())
                .packageCreatedAt(order.getPackageCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(readItems(order.getItemsJson()))
                .build();
    }

    private List<TrendyolGoDtos.OrderItemResponse> readItems(String json) {
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
        void apply(TrendyolGoDtos.Credentials credentials, String orderId);
    }
}
