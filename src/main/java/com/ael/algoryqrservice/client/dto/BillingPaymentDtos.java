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
            String status,
            String planId,
            LocalDateTime currentPeriodStart,
            LocalDateTime currentPeriodEnd,
            boolean cancelAtPeriodEnd
    ) {
    }
}
