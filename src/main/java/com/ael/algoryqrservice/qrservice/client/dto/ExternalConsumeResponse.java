package com.ael.algoryqrservice.qrservice.client.dto;

public record ExternalConsumeResponse(
        Long entitlementId,
        Long purchaseId,
        Integer usedQuantity,
        Integer remainingQuantity,
        boolean unlimited
) {
}
