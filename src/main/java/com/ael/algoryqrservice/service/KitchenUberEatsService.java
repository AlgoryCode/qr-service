package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsOrder;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsOrderRepository;
import com.ael.algoryqrservice.integration.ubereats.service.UberEatsConnectionService;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KitchenUberEatsService {

    private final UberEatsConnectionService connectionService;
    private final UberEatsOrderRepository orderRepository;
    private final UberEatsClient uberEatsClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listActiveForOwner(Long merchantId) {
        UberEatsConnection connection = connectedOrNull(merchantId);
        if (connection == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now(KitchenUberEatsMapper.ZONE);
        return orderRepository
                .findKitchenOrders(connection.getId(), KitchenUberEatsMapper.KITCHEN_STATUSES)
                .stream()
                .filter(order -> KitchenUberEatsMapper.isActiveKitchenOrder(order, today))
                .map(this::toKitchenOrder)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listManagedForOwner(Long merchantId) {
        UberEatsConnection connection = connectedOrNull(merchantId);
        if (connection == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now(KitchenUberEatsMapper.ZONE);
        return orderRepository
                .findByConnectionIdAndPackageCreatedAtBetweenOrderByPackageCreatedAtDesc(
                        connection.getId(),
                        today.minusDays(31).atStartOfDay(),
                        today.plusDays(1).atStartOfDay().minusNanos(1)
                )
                .stream()
                .filter(order -> {
                    String status = KitchenUberEatsMapper.normalizeStatus(order.getPackageStatus());
                    return !status.isEmpty() && !"created".equals(status) && !"unassigned".equals(status);
                })
                .map(this::toKitchenOrder)
                .toList();
    }

    @Transactional
    public MenuOrderDtos.OrderResponse markReady(Long merchantId, Long orderId) {
        UberEatsConnection connection = connectionService.requireConnectedForUser(merchantId);
        UberEatsOrder order = orderRepository.findByIdAndConnectionId(orderId, connection.getId())
                .orElseThrow(() -> new NotFoundException("Uber Eats siparişi bulunamadı"));
        String status = KitchenUberEatsMapper.normalizeStatus(order.getPackageStatus());
        if ("prepared".equals(status) || "ready".equals(status)) {
            return toKitchenOrder(order);
        }
        if (!"accepted".equals(status) && !"picking".equals(status)) {
            throw new BadRequestException("Bu Uber Eats siparişi hazır işaretlenemez");
        }
        uberEatsClient.markReady(connectionService.decrypt(connection), order.getExternalOrderId());
        order.setPackageStatus("Prepared");
        return toKitchenOrder(orderRepository.save(order));
    }

    private UberEatsConnection connectedOrNull(Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        UberEatsConnection connection = connectionService.findByUserId(merchantId);
        if (connection == null
                || connection.getStatus() != UberEatsConnectionStatus.CONNECTED
                || connection.getRestaurantId() == null
                || connection.getRestaurantId().isBlank()) {
            return null;
        }
        return connection;
    }

    private MenuOrderDtos.OrderResponse toKitchenOrder(UberEatsOrder order) {
        return KitchenUberEatsMapper.toKitchenOrder(order, readItems(order.getItemsJson()));
    }

    private List<UberEatsDtos.OrderItemResponse> readItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
