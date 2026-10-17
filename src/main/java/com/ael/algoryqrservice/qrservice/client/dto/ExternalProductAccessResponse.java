package com.ael.algoryqrservice.qrservice.client.dto;

public record ExternalProductAccessResponse(
        boolean allowed,
        String packageCode,
        String featureCode,
        String scopeCode
) {
}
