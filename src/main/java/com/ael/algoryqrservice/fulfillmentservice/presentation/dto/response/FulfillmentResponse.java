package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FulfillmentResponse(
        Long id,
        String eventId,
        String eventType,
        Long purchaseId,
        Long userId,
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
