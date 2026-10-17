package com.ael.algoryqrservice.paymentservice.domain.model;

import java.util.Map;

public record CardStorageSession(
        String conversationId,
        String actionUrl,
        Map<String, String> fields
) {
}
