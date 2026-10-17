package com.ael.algoryqrservice.fulfillmentservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PackageFulfillment(
        Long id,
        String eventId,
        String eventType,
        Long purchaseId,
        Long merchantId,
        Long packageId,
        String packageCode,
        String conversationId,
        String paymentId,
        String subscriptionId,
        Integer billingCycleNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal amount,
        String currency,
        FulfillmentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
