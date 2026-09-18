package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreCourier;
import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderItem;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import com.ael.algoryqrservice.store.model.dto.StorePublicDtos;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StorefrontService {

    private final StorefrontResolver storefrontResolver;
    private final StoreCatalogService storeCatalogService;
    private final StoreOrderService storeOrderService;
    private final StoreOrderRateLimiter storeOrderRateLimiter;
    private final StoreOpeningHours storeOpeningHours;
    private final MerchantMapper merchantMapper;
    private final MerchantRepository merchantRepository;
    private final StoreUrlBuilder storeUrlBuilder;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public StorePublicDtos.StorefrontResponse getStorefront(Long storeNo, String token) {
        Merchant merchant = storefrontResolver.requirePublished(storeNo, token);
        return StorePublicDtos.StorefrontResponse.builder()
                .store(toSummary(merchant))
                .categories(storeCatalogService.listCatalog(merchant.getCatalogMenuId()))
                .build();
    }

    @Transactional
    public StorePublicDtos.OrderTrackingResponse placeOrder(
            Long storeNo,
            String token,
            StorePublicDtos.CreateOrderRequest request,
            String clientIp
    ) {
        storeOrderRateLimiter.check(clientIp, request.customerPhone());
        Merchant merchant = storefrontResolver.requirePublished(storeNo, token);
        Long customerId = securityUtils.findCurrentCustomerId().orElse(null);
        StoreOrder order = storeOrderService.placeOrder(merchant, request, customerId);
        return toTracking(order, merchant);
    }

    @Transactional(readOnly = true)
    public StorePublicDtos.OrderTrackingResponse trackOrder(String orderPublicToken) {
        StoreOrder order = storeOrderService.requireByPublicToken(orderPublicToken);
        Merchant merchant = merchantRepository.findByIdAndDeletedFalse(order.getMerchantId()).orElseThrow();
        return toTracking(order, merchant);
    }

    private StorePublicDtos.StoreSummary toSummary(Merchant merchant) {
        return StorePublicDtos.StoreSummary.builder()
                .storeNo(merchant.getStoreNo())
                .handle(storeUrlBuilder.buildHandle(merchant))
                .canonicalHandle(storeUrlBuilder.buildHandle(merchant))
                .businessName(merchant.getBusinessName())
                .logoUrl(merchant.getLogoUrl())
                .coverUrl(merchant.getCoverUrl())
                .phone(merchant.getPhone())
                .address(merchant.getAddress())
                .city(merchant.getCity())
                .district(merchant.getDistrict())
                .open(storeOpeningHours.isOpenAt(merchant, LocalDateTime.now()))
                .minOrderAmount(merchant.getMinOrderAmount())
                .deliveryFee(merchant.getDeliveryFee())
                .freeDeliveryThreshold(merchant.getFreeDeliveryThreshold())
                .avgPrepMinutes(merchant.getAvgPrepMinutes())
                .deliveryTypes(merchant.getDeliveryTypes())
                .paymentMethods(merchant.getPaymentMethods())
                .currency(merchant.getCurrency())
                .workingHours(merchantMapper.toDeliverySettings(merchant).workingHours())
                .build();
    }

    private StorePublicDtos.OrderTrackingResponse toTracking(StoreOrder order, Merchant merchant) {
        Optional<StoreCourier> courier = shouldRevealCourier(order.getStatus())
                ? storeOrderService.findCourier(order.getCourierId())
                : Optional.empty();
        return StorePublicDtos.OrderTrackingResponse.builder()
                .orderNo(order.getOrderNo())
                .publicToken(order.getPublicToken())
                .status(order.getStatus())
                .deliveryType(order.getDeliveryType())
                .paymentMethod(order.getPaymentMethod())
                .businessName(merchant.getBusinessName())
                .customerName(order.getCustomerName())
                .addressText(order.getAddressText())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .avgPrepMinutes(merchant.getAvgPrepMinutes())
                .courierName(courier.map(StoreCourier::getFullName).orElse(null))
                .courierPhone(courier.map(StoreCourier::getPhone).orElse(null))
                .items(toItems(order.getItems()))
                .createdAt(order.getCreatedAt())
                .confirmedAt(order.getConfirmedAt())
                .dispatchedAt(order.getDispatchedAt())
                .deliveredAt(order.getDeliveredAt())
                .build();
    }

    /** Courier contact details only become public once the order is actually out for delivery. */
    private boolean shouldRevealCourier(StoreOrderStatus status) {
        return status == StoreOrderStatus.ON_THE_WAY || status == StoreOrderStatus.DELIVERED;
    }

    private List<StorePublicDtos.OrderItemResponse> toItems(List<StoreOrderItem> items) {
        return items.stream()
                .map(item -> StorePublicDtos.OrderItemResponse.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .note(item.getNote())
                        .options(item.getSelectedOptions())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();
    }
}
