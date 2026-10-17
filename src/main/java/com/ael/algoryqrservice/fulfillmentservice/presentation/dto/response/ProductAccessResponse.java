package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response;

public record ProductAccessResponse(
        boolean allowed,
        String packageCode,
        String featureCode,
        String scopeCode
) {
}
