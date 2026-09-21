package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.integration.yemeksepeti.client.YemekSepetiClient;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiConnection;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiConnectionStatus;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiOrder;
import com.ael.algoryqrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.ael.algoryqrservice.integration.yemeksepeti.repository.YemekSepetiOrderRepository;
import com.ael.algoryqrservice.integration.yemeksepeti.service.YemekSepetiConnectionService;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.enums.OrderSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KitchenYemekSepetiService {

    static final List<String> KITCHEN_STATUSES = List.of(
            "received",
            "accepted",
            "picking",
            "prepared",
            "ready",
            "ready_for_pickup",
            "dispatched"
    );

    private final YemekSepetiConnectionService connectionService;
    private final YemekSepetiOrderRepository orderRepository;
    private final YemekSepetiClient yemekSepetiClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listActiveForOwner(Long ownerUserId) {
        YemekSepetiConnection connection = connectedOrNull(ownerUserId);
        if (connection == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now(KitchenUberEatsMapper.ZONE);
        return orderRepository
                .findKitchenOrders(connection.getId(), KITCHEN_STATUSES)
                .stream()
                .filter(order -> KitchenUberEatsMapper.isActiveMarketplaceKitchenOrder(
                        order.getPackageStatus(),
                        order.getUpdatedAt(),
                        order.getPackageCreatedAt(),
                        today,
                        true
                ))
                .map(this::toKitchenOrder)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listManagedForOwner(Long ownerUserId) {
        YemekSepetiConnection connection = connectedOrNull(ownerUserId);
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
                .map(this::toKitchenOrder)
                .toList();
    }

    @Transactional
    public MenuOrderDtos.OrderResponse markReady(Long ownerUserId, Long orderId) {
        YemekSepetiConnection connection = connectionService.requireConnectedForUser(ownerUserId);
        YemekSepetiOrder order = orderRepository.findByIdAndConnectionId(orderId, connection.getId())
                .orElseThrow(() -> new NotFoundException("Yemeksepeti siparişi bulunamadı"));
        String status = KitchenUberEatsMapper.normalizeStatus(order.getPackageStatus());
        if ("readyforpickup".equals(status) || "prepared".equals(status) || "ready".equals(status) || "dispatched".equals(status)) {
            return toKitchenOrder(order);
        }
        if (!"received".equals(status) && !"accepted".equals(status) && !"picking".equals(status)) {
            throw new BadRequestException("Bu Yemeksepeti siparişi hazır işaretlenemez");
        }
        yemekSepetiClient.markReady(connectionService.decrypt(connection), order.getExternalOrderId());
        order.setPackageStatus("READY_FOR_PICKUP");
        return toKitchenOrder(orderRepository.save(order));
    }

    private YemekSepetiConnection connectedOrNull(Long ownerUserId) {
        if (ownerUserId == null) {
            return null;
        }
        YemekSepetiConnection connection = connectionService.findByUserId(ownerUserId);
        if (connection == null
                || connection.getStatus() != YemekSepetiConnectionStatus.CONNECTED
                || connection.getVendorId() == null
                || connection.getVendorId().isBlank()) {
            return null;
        }
        return connection;
    }

    private MenuOrderDtos.OrderResponse toKitchenOrder(YemekSepetiOrder order) {
        List<MenuOrderDtos.OrderItemResponse> items = new ArrayList<>();
        for (YemekSepetiDtos.OrderItemResponse item : readItems(order.getItemsJson())) {
            items.add(KitchenUberEatsMapper.toTicketItem(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    firstText(item.getDetail(), item.getOptions())
            ));
        }
        return KitchenUberEatsMapper.toTicket(
                order.getId(),
                order.getPackageStatus(),
                order.getCustomerName(),
                order.getNote(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getPackageCreatedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items,
                OrderSource.YEMEKSEPETI,
                "Yemeksepeti"
        );
    }

    private List<YemekSepetiDtos.OrderItemResponse> readItems(String json) {
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

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
