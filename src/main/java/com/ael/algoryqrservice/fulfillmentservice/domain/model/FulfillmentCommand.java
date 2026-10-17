package com.ael.algoryqrservice.fulfillmentservice.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record FulfillmentCommand(
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
        Map<String, Object> sourceMetadata
) {
}
