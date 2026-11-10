package com.ael.algoryqrservice.qrservice.order.service;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnectionStatus;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsOrder;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.qrservice.integration.ubereats.repository.UberEatsOrderRepository;
import com.ael.algoryqrservice.qrservice.integration.service.ubereats.UberEatsConnectionService;
import com.ael.algoryqrservice.qrservice.order.dto.MenuOrderDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import com.ael.algoryqrservice.qrservice.branch.service.BranchScope;

@Service
@RequiredArgsConstructor
public class KitchenUberEatsService {

    private final UberEatsConnectionService connectionService;
    private final UberEatsOrderRepository orderRepository;
    private final UberEatsClient uberEatsClient;
    private final ObjectMapper objectMapper;

    /** Her şubenin kendi bağlantısı var; başka şubeye ait bağlantıların siparişleri bu mutfakta görünmez. */
    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listActiveForBranch(Long merchantId, Long branchId) {
        return connectionService.findConnectedForMerchant(merchantId).stream()
                .filter(connection -> BranchScope.serves(connection.getBranchId(), branchId))
                .flatMap(connection -> listActive(connection).stream())
                .toList();
    }

    private List<MenuOrderDtos.OrderResponse> listActive(UberEatsConnection connection) {
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

    /**
     * Orders of the last month for the connections whose branch passes [listsConnectionBranch].
     * Callers decide which menu shows a connection, so one order is never listed under several menus.
     */
    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listManagedForOwner(Long merchantId, Predicate<Long> listsConnectionBranch) {
        LocalDate today = LocalDate.now(KitchenUberEatsMapper.ZONE);
        return connectionService.findConnectedForMerchant(merchantId).stream()
                .filter(connection -> listsConnectionBranch.test(connection.getBranchId()))
                .flatMap(connection -> orderRepository
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
                        .map(this::toKitchenOrder))
                .toList();
    }

    @Transactional
    public MenuOrderDtos.OrderResponse markReady(Long merchantId, Long orderId) {
        UberEatsOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Uber Eats siparişi bulunamadı"));
        UberEatsConnection connection = connectionService.requireConnectedForUser(merchantId, order.getConnectionId());
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
