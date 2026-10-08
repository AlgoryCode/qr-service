package com.ael.algoryqrservice.model.dto;

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
