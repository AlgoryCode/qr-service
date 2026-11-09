package com.ael.algoryqrservice.qrservice.order.service;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.client.YemekSepetiClient;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.YemekSepetiConnection;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.YemekSepetiConnectionStatus;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.YemekSepetiOrder;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.repository.YemekSepetiOrderRepository;
import com.ael.algoryqrservice.qrservice.integration.service.yemeksepeti.YemekSepetiConnectionService;
import com.ael.algoryqrservice.qrservice.order.dto.MenuOrderDtos;
import com.ael.algoryqrservice.qrservice.model.enums.OrderSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.ael.algoryqrservice.qrservice.branch.service.BranchScope;
import com.ael.algoryqrservice.qrservice.order.service.KitchenUberEatsMapper;

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

    /** Her şubenin kendi bağlantısı var; başka şubeye ait bağlantıların siparişleri bu mutfakta görünmez. */
    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listActiveForBranch(Long merchantId, Long branchId) {
        return connectionService.findConnectedForMerchant(merchantId).stream()
                .filter(connection -> BranchScope.serves(connection.getBranchId(), branchId))
                .flatMap(connection -> listActive(connection).stream())
                .toList();
    }

    private List<MenuOrderDtos.OrderResponse> listActive(YemekSepetiConnection connection) {
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
    public List<MenuOrderDtos.OrderResponse> listManagedForOwner(Long merchantId) {
        LocalDate today = LocalDate.now(KitchenUberEatsMapper.ZONE);
        return connectionService.findConnectedForMerchant(merchantId).stream()
                .flatMap(connection -> orderRepository
                        .findByConnectionIdAndPackageCreatedAtBetweenOrderByPackageCreatedAtDesc(
                                connection.getId(),
                                today.minusDays(31).atStartOfDay(),
                                today.plusDays(1).atStartOfDay().minusNanos(1)
                        )
                        .stream()
                        .map(this::toKitchenOrder))
                .toList();
    }

    @Transactional
    public MenuOrderDtos.OrderResponse markReady(Long merchantId, Long orderId) {
        YemekSepetiOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Yemeksepeti siparişi bulunamadı"));
        YemekSepetiConnection connection = connectionService.requireConnectedForUser(merchantId, order.getConnectionId());
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
