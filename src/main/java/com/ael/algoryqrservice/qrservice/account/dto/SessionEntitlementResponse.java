package com.ael.algoryqrservice.qrservice.account.dto;

public record SessionEntitlementResponse(
        String productCode,
        String featureCode,
        String scopeCode,
        Integer quantity,
        boolean unlimited,
        Integer usedQuantity,
        String source
) {
}
