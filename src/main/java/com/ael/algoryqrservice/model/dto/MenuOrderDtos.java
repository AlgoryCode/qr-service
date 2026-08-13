package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class MenuOrderDtos {

    private MenuOrderDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemRequest {
        @NotNull
        private Long productId;

        @NotNull
        @Min(1)
        private Integer quantity;

        @Size(max = 500)
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCartRequest {
        @NotNull
        @Valid
        private List<CartItemRequest> items;

        @Size(max = 1000)
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiterCreateOrderRequest {
        @NotNull
        private Long tableId;

        @NotNull
        @Valid
        @Size(min = 1)
        private List<CartItemRequest> items;

        @Size(max = 1000)
        private String note;

        @Size(max = 2000)
        private String waiterNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private BigDecimal unitPrice;
        private int quantity;
        private String note;
        private BigDecimal lineTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private Long id;
        private Long menuId;
        private Long tableId;
        private String tableName;
        private UUID tableSessionId;
        private Long customerId;
        private String customerName;
        private String customerEmail;
        private MenuOrderStatus status;
        private BigDecimal totalAmount;
        private String currency;
        private String note;
        private Long waiterId;
        private String waiterName;
        private String waiterNote;
        private List<OrderItemResponse> items;
        private LocalDateTime submittedAt;
        private LocalDateTime confirmedAt;
        private LocalDateTime rejectedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
