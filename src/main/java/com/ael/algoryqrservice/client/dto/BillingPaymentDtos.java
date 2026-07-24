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
    }
}
