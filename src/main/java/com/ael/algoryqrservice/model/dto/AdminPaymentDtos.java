package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminPaymentDtos {

    private AdminPaymentDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummaryResponse {
        private String conversationId;
        private String paymentId;
        private String paymentTransactionId;
        private String accountId;
        private Long userId;
        private String buyerEmail;
        private String buyerName;
        private String serviceName;
        private String sourceReferenceId;
        private BigDecimal price;
        private BigDecimal paidPrice;
        private BigDecimal refundedAmount;
        private BigDecimal remainingAmount;
        private String currency;
        private String status;
        private String paymentType;
        private String paymentStyle;
        private boolean verificationOnly;
        private String errorCode;
        private String errorMessage;
        private Long purchaseId;
        private String packageName;
        private boolean refundEligible;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDetailResponse {
        private String conversationId;
        private String paymentId;
        private String paymentTransactionId;
        private String basketId;
        private String accountId;
        private Long userId;
        private String buyerEmail;
        private String buyerName;
        private String serviceName;
        private String sourceReferenceId;
        private BigDecimal price;
        private BigDecimal paidPrice;
        private BigDecimal refundedAmount;
        private BigDecimal remainingAmount;
        private String currency;
        private String status;
        private String paymentType;
        private String paymentStyle;
        private Integer bankInstallmentCount;
        private String subscriptionId;
        private Integer billingCycleNumber;
        private boolean verificationOnly;
        private String errorCode;
        private String errorMessage;
        private Long purchaseId;
        private String packageName;
        private String packageCode;
        private boolean refundEligible;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentPageResponse {
        private List<PaymentSummaryResponse> content;
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
    public static class RefundRequest {
        @DecimalMin(value = "0.01", inclusive = true)
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundResponse {
        private String conversationId;
        private String paymentTransactionId;
        private BigDecimal refundedPrice;
        private String status;
        private Long purchaseId;
    }
}
