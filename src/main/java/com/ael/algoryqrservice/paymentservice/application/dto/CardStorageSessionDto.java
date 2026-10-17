package com.ael.algoryqrservice.paymentservice.application.dto;

import java.util.Map;

public record CardStorageSessionDto(
        String conversationId,
        String actionUrl,
        Map<String, String> fields
) {
}
