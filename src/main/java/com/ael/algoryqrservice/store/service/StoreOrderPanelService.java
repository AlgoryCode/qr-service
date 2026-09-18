package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreCourier;
import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import com.ael.algoryqrservice.store.model.dto.StoreOrderDtos;
import com.ael.algoryqrservice.store.repository.StoreCourierRepository;
import com.ael.algoryqrservice.store.repository.StoreOrderRepository;
import com.ael.algoryqrservice.store.repository.StoreOrderStatusHistoryRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StoreOrderPanelService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<StoreOrderStatus> OPEN_STATUSES = List.of(
            StoreOrderStatus.PENDING,
            StoreOrderStatus.CONFIRMED,
            StoreOrderStatus.PREPARING,
            StoreOrderStatus.READY,
            StoreOrderStatus.ON_THE_WAY
    );

    private final StoreOrderRepository storeOrderRepository;
    private final StoreOrderStatusHistoryRepository statusHistoryRepository;
    private final StoreCourierRepository storeCourierRepository;
    private final StoreOrderService storeOrderService;
    private final StoreOrderMapper storeOrderMapper;
    private final MerchantService merchantService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public StoreOrderDtos.OrderPage list(
            Collection<StoreOrderStatus> statuses,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        Merchant merchant = merchantService.requireCurrentMerchant();
        Collection<StoreOrderStatus> effectiveStatuses = statuses == null || statuses.isEmpty() ? null : statuses;
        Page<StoreOrder> result = storeOrderRepository.search(
                merchant.getId(),
                effectiveStatuses,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE))
        );
        Map<Long, StoreCourier> couriers = loadCouriers(merchant.getId());
        return StoreOrderDtos.OrderPage.builder()
                .content(result.getContent().stream()
                        .map(order -> storeOrderMapper.toSummary(order, couriers.get(order.getCourierId())))
                        .toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .openCounts(countOpenByStatus(merchant.getId()))
                .build();
    }

    @Transactional(readOnly = true)
    public StoreOrderDtos.OrderDetail get(Long orderId) {
        Merchant merchant = merchantService.requireCurrentMerchant();
        StoreOrder order = storeOrderService.requireOrder(merchant.getId(), orderId);
        return toDetail(merchant.getId(), order);
    }

    @Transactional
    public StoreOrderDtos.OrderDetail advance(Long orderId, StoreOrderStatus target, String reason) {
        Merchant merchant = merchantService.requireCurrentMerchant();
        StoreOrder order = storeOrderService.advance(
                merchant.getId(),
                orderId,
                target,
                securityUtils.getCurrentUserId(),
                reason
        );
        return toDetail(merchant.getId(), order);
    }

    @Transactional
    public StoreOrderDtos.OrderDetail assignCourier(Long orderId, Long courierId) {
        Merchant merchant = merchantService.requireCurrentMerchant();
        StoreOrder order = storeOrderService.assignCourier(
                merchant.getId(),
                orderId,
                courierId,
                securityUtils.getCurrentUserId()
        );
        return toDetail(merchant.getId(), order);
    }

    private StoreOrderDtos.OrderDetail toDetail(Long merchantId, StoreOrder order) {
        StoreCourier courier = order.getCourierId() == null
                ? null
                : storeCourierRepository.findByIdAndMerchantIdAndDeletedFalse(order.getCourierId(), merchantId).orElse(null);
        return storeOrderMapper.toDetail(order, courier, statusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    private Map<Long, StoreCourier> loadCouriers(Long merchantId) {
        Map<Long, StoreCourier> byId = new LinkedHashMap<>();
        storeCourierRepository.findByMerchantIdAndDeletedFalseOrderByFullNameAsc(merchantId)
                .forEach(courier -> byId.put(courier.getId(), courier));
        return byId;
    }

    private Map<StoreOrderStatus, Long> countOpenByStatus(Long merchantId) {
        Map<StoreOrderStatus, Long> counts = new EnumMap<>(StoreOrderStatus.class);
        for (StoreOrderStatus status : OPEN_STATUSES) {
            counts.put(status, storeOrderRepository.countByMerchantIdAndStatusIn(merchantId, List.of(status)));
        }
        return counts;
    }
}
