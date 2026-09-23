package com.ael.algoryqrservice.client.dto;

public record ExternalProductAccessResponse(
        boolean allowed,
        String packageCode,
        String featureCode,
        String scopeCode
) {
}
