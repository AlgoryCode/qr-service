package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import com.ael.algoryqrservice.model.enums.CouponLogAction;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CouponDtos {

    private CouponDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank
        @Size(min = 4, max = 32)
        private String code;
        @NotNull
        private CouponDiscountType discountType;
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal discountValue;
        @NotNull
        private LocalDateTime expiresAt;
        private LocalDateTime validFrom;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevokeRequest {
        @NotBlank
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String code;
        private CouponDiscountType discountType;
        private BigDecimal discountValue;
        private LocalDateTime expiresAt;
        private LocalDateTime validFrom;
        private CouponStatus status;
        private Long reservedPurchaseId;
        private Long usedPurchaseId;
        private Long usedByUserId;
        private LocalDateTime usedAt;
        private Long createdByAdminId;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageResponse {
        private List<Response> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewResponse {
        private String code;
        private CouponDiscountType discountType;
        private BigDecimal discountValue;
        private LocalDateTime expiresAt;
        private boolean usable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogResponse {
        private Long id;
        private Long couponId;
        private Long userId;
        private Long purchaseId;
        private CouponLogAction action;
        private BigDecimal listPrice;
        private BigDecimal discountAmount;
        private BigDecimal payable;
        private String message;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogPageResponse {
        private List<LogResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
    }
}
