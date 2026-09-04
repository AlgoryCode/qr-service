package com.ael.algoryqrservice.integration.ubereats.model.dto;

import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnectionStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class UberEatsDtos {

    private UberEatsDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsertConnectionRequest {
        @NotBlank
        private String sellerId;

        private String apiKey;
        private String apiSecret;
        private String restaurantId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionResponse {
        private Long id;
        private String sellerId;
        private String apiKeyMasked;
        private String restaurantId;
        private String restaurantName;
        private UberEatsConnectionStatus status;
        private String lastError;
        private LocalDateTime lastSyncedAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestaurantResponse {
        private String id;
        private String name;
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductResponse {
        private String id;
        private String name;
        private String description;
        private String categoryName;
        private BigDecimal price;
        private String currency;
        private String imageUrl;
        private boolean available;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductPageResponse {
        private List<ProductResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private String options;
        private String detail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private Long id;
        private String externalOrderId;
        private String orderNumber;
        private String deliveryType;
        private String paymentMethod;
        private String packageStatus;
        private BigDecimal totalAmount;
        private String currency;
        private String customerName;
        private String customerPhone;
        private String deliveryAddress;
        private String note;
        private LocalDateTime packageCreatedAt;
        private LocalDateTime updatedAt;
        @Builder.Default
        private List<OrderItemResponse> items = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPageResponse {
        private List<OrderResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncOrdersResponse {
        private int upserted;
        private int lookbackHours;
        private LocalDate from;
        private LocalDate to;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Credentials {
        private String sellerId;
        private String apiKey;
        private String apiSecret;
        private String restaurantId;
    }
}
