package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response;

public record FieldErrorResponse(
        String field,
        String message
) {
}
