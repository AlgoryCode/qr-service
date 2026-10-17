package com.ael.algoryqrservice.paymentservice.presentation.dto.response;

import java.util.Map;

public record CardStorageSessionResponse(
        String conversationId,
        String actionUrl,
        Map<String, String> fields
) {
}
