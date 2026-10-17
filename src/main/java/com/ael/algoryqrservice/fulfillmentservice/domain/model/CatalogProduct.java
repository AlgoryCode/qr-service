package com.ael.algoryqrservice.fulfillmentservice.domain.model;

public record CatalogProduct(
        Long id,
        String code,
        String name,
        String featureCode,
        String scopeCode
) {
}
