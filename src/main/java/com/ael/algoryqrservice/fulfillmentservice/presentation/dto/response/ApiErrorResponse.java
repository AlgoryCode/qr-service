package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String errorCode,
        String path,
        List<FieldErrorResponse> fieldErrors
) {
}
