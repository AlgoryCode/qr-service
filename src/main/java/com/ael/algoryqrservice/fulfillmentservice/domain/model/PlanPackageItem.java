package com.ael.algoryqrservice.fulfillmentservice.domain.model;

public record PlanPackageItem(
        Long id,
        Long productId,
        String productCode,
        String featureCode,
        String scopeCode,
        ProductType productType,
        Integer quantity,
        boolean unlimited
) {
}
