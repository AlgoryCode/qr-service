package com.ael.algoryqrservice.fulfillmentservice.domain.model;

public record ProductAccessView(
        boolean allowed,
        String packageCode,
        String featureCode,
        String scopeCode
) {
    public static ProductAccessView denied(String packageCode) {
        return new ProductAccessView(false, packageCode, null, null);
    }
}
