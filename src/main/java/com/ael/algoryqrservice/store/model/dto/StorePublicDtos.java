package com.ael.algoryqrservice.store.model.dto;

import com.ael.algoryqrservice.model.SelectedMenuOption;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.store.model.StoreDeliveryType;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import com.ael.algoryqrservice.store.model.StorePaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class StorePublicDtos {

    private StorePublicDtos() {
    }

    @Builder
    public record StoreProduct(
            Long productId,
            String name,
            String description,
            BigDecimal price,
            String currency,
            String imageUrl,
            boolean available,
            List<MenuDtos.MenuProductOptionGroupResponse> optionGroups
    ) {
    }

    @Builder
    public record StoreCategory(
            Long categoryId,
            String slug,
            String name,
            List<StoreProduct> products
    ) {
    }

    @Builder
    public record StoreSummary(
            Long storeNo,
            String handle,
            String canonicalHandle,
            String businessName,
            String logoUrl,
            String coverUrl,
            String phone,
            String address,
            String city,
            String district,
            boolean open,
            BigDecimal minOrderAmount,
            BigDecimal deliveryFee,
            BigDecimal freeDeliveryThreshold,
            int avgPrepMinutes,
            Set<StoreDeliveryType> deliveryTypes,
            Set<StorePaymentMethod> paymentMethods,
            String currency,
            List<StoreDtos.WorkingHour> workingHours
    ) {
    }

    @Builder
    public record StorefrontResponse(StoreSummary store, List<StoreCategory> categories) {
    }

    public record SelectedOptionRequest(@NotNull Long groupId, @NotNull Long optionId) {
    }

    public record OrderItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity,
            @Size(max = 500) String note,
            List<@Valid SelectedOptionRequest> options
    ) {
    }

    public record DeliveryAddressRequest(
            @Size(max = 1000) String addressText,
            @Size(max = 80) String city,
            @Size(max = 80) String district,
            @Size(max = 32) String buildingNo,
            @Size(max = 32) String floorNo,
            @Size(max = 32) String doorNo,
            @Size(max = 500) String directions,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record CreateOrderRequest(
            @NotBlank @Size(max = 160) String customerName,
            @NotBlank @Size(max = 32) String customerPhone,
            @NotNull StoreDeliveryType deliveryType,
            @NotNull StorePaymentMethod paymentMethod,
            @Valid DeliveryAddressRequest address,
            @Size(max = 500) String note,
            @NotEmpty List<@Valid OrderItemRequest> items
    ) {
    }

    @Builder
    public record OrderItemResponse(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            String note,
            List<SelectedMenuOption> options,
            BigDecimal lineTotal
    ) {
    }

    @Builder
    public record OrderTrackingResponse(
            String orderNo,
            String publicToken,
            StoreOrderStatus status,
            StoreDeliveryType deliveryType,
            StorePaymentMethod paymentMethod,
            String businessName,
            String customerName,
            String addressText,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal totalAmount,
            String currency,
            int avgPrepMinutes,
            String courierName,
            String courierPhone,
            List<OrderItemResponse> items,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt,
            LocalDateTime dispatchedAt,
            LocalDateTime deliveredAt
    ) {
    }
}
