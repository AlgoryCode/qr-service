package com.ael.algoryqrservice.fulfillmentservice.domain.model;

import java.math.BigDecimal;
import java.util.Map;

public record PaymentCompletedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String paymentId,
        String conversationId,
        String serviceName,
        String sourceReferenceId,
        Map<String, Object> sourceMetadata,
        String purchaseId,
        String userId,
        String packageId,
        String packageCode,
        String paymentStyle,
        Integer bankInstallmentCount,
        String subscriptionId,
        Integer billingCycleNumber,
        String periodStart,
        String periodEnd,
        BigDecimal amount,
        String currency,
        String errorCode,
        String failureReason
) {
}
