package com.ael.algoryqrservice.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class BillingPaymentDtos {
    private BillingPaymentDtos() {
    }

    public record PaymentMethod(
            String id,
            String cardAlias,
            String brand,
            String lastFour,
            Integer expiryMonth,
            Integer expiryYear
    ) {
    }

    public record InstallmentOption(
            Integer count,
            BigDecimal totalAmount,
            BigDecimal installmentAmount
    ) {
    }

    public record InstallmentProvider(
            String binNumber,
            String cardAssociation,
            String cardFamily,
            String bankName,
            List<InstallmentOption> options
    ) {
    }

    public record InstallmentOptions(List<InstallmentOption> options) {
    }

    public record Subscription(
            String id,
            Long paymentMethodId,
            String status,
            BigDecimal amount,
            String currency,
            Integer completedCycleCount,
            Integer totalCycleCount,
            LocalDateTime nextChargeAt,
            Boolean cancelAtPeriodEnd
    ) {
    }

    public record RefundResult(
            String conversationId,
            String paymentTransactionId,
            BigDecimal refundedPrice,
            String status
    ) {
    }

    public record CardVerificationInit(
            String conversationId,
            String token,
            String paymentPageUrl,
            String checkoutFormContent
    ) {
    }

    public record RefundablePayment(
            String conversationId,
            String paymentId,
            String paymentTransactionId,
            String status,
            BigDecimal paidPrice,
            BigDecimal refundedAmount,
            BigDecimal remaining
    ) {
        public boolean isSuccess() {
            return status != null && "SUCCESS".equalsIgnoreCase(status.trim());
        }

        public boolean isCardVerificationComplete() {
            if (status == null) {
                return false;
            }
            String normalized = status.trim();
            return "SUCCESS".equalsIgnoreCase(normalized) || "REFUNDED".equalsIgnoreCase(normalized);
        }

        public boolean isFailed() {
            return status != null && "FAILURE".equalsIgnoreCase(status.trim());
        }
    }

    public record StoredCardCharge(
            String conversationId,
            String status
    ) {
        public boolean isSuccess() {
            return status != null && "SUCCESS".equalsIgnoreCase(status.trim());
        }

        public boolean isInitiated() {
            return status != null && "INITIATED".equalsIgnoreCase(status.trim());
        }
    }

    public record PaymentDetail(
            String conversationId,
            String paymentId,
            String paymentTransactionId,
            String basketId,
            String accountId,
            String buyerEmail,
            String buyerName,
            String serviceName,
            String sourceReferenceId,
            BigDecimal price,
            BigDecimal paidPrice,
            BigDecimal refundedAmount,
            BigDecimal remainingAmount,
            String currency,
            String status,
            String paymentType,
            String paymentStyle,
            Integer bankInstallmentCount,
            String subscriptionId,
            Integer billingCycleNumber,
            boolean verificationOnly,
            String errorCode,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public boolean isSuccess() {
            return status != null && "SUCCESS".equalsIgnoreCase(status.trim());
        }
    }

    public record PaymentPage(
            List<PaymentDetail> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
