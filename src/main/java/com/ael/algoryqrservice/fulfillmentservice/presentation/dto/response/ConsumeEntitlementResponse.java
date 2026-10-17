package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response;

public record ConsumeEntitlementResponse(
        Long entitlementId,
        Long purchaseId,
        Integer usedQuantity,
        Integer remainingQuantity,
        boolean unlimited
) {
}
