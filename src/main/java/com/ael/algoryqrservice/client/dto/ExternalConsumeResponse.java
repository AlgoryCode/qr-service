package com.ael.algoryqrservice.client.dto;

public record ExternalConsumeResponse(
        Long entitlementId,
        Long purchaseId,
        Integer usedQuantity,
        Integer remainingQuantity,
        boolean unlimited
) {
}
