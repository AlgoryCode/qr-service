package com.ael.algoryqrservice.store.model.dto;

import com.ael.algoryqrservice.store.model.StoreDeliveryType;
import com.ael.algoryqrservice.store.model.StoreOrderActorType;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import com.ael.algoryqrservice.store.model.StorePaymentMethod;
import com.ael.algoryqrservice.store.model.StorePaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class StoreOrderDtos {

    private StoreOrderDtos() {
    }

    @Builder
    public record Address(
            String addressText,
            String city,
            String district,
            String buildingNo,
            String floorNo,
            String doorNo,
            String directions,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    @Builder
    public record CourierBrief(Long id, String fullName, String phone) {
    }

    @Builder
    public record OrderSummary(
            Long id,
            String orderNo,
            StoreOrderStatus status,
            StoreDeliveryType deliveryType,
            StorePaymentMethod paymentMethod,
            StorePaymentStatus paymentStatus,
            String customerName,
            String customerPhone,
            BigDecimal totalAmount,
            String currency,
            int itemCount,
            CourierBrief courier,
            LocalDateTime createdAt
    ) {
    }

    @Builder
    public record StatusEvent(
            StoreOrderStatus fromStatus,
            StoreOrderStatus toStatus,
            StoreOrderActorType changedByType,
            String note,
            LocalDateTime createdAt
    ) {
    }

    @Builder
    public record OrderDetail(
            Long id,
            String orderNo,
            String publicToken,
            StoreOrderStatus status,
            StoreDeliveryType deliveryType,
            StorePaymentMethod paymentMethod,
            StorePaymentStatus paymentStatus,
            Long customerId,
            String customerName,
            String customerPhone,
            Address address,
            String note,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            String currency,
            CourierBrief courier,
            List<StorePublicDtos.OrderItemResponse> items,
            List<StatusEvent> history,
            String rejectReason,
            String cancelReason,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt,
            LocalDateTime readyAt,
            LocalDateTime dispatchedAt,
            LocalDateTime deliveredAt
    ) {
    }

    @Builder
    public record OrderPage(
            List<OrderSummary> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            Map<StoreOrderStatus, Long> openCounts
    ) {
    }

    public record TransitionRequest(@Size(max = 500) String reason) {
    }

    public record AssignCourierRequest(@NotNull Long courierId) {
    }
}
