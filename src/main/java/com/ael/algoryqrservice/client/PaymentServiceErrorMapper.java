package com.ael.algoryqrservice.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PaymentServiceErrorMapper {

    private PaymentServiceErrorMapper() {
    }

    static int httpStatus(int upstreamStatus) {
        if (upstreamStatus >= 400 && upstreamStatus < 500 && upstreamStatus != 401 && upstreamStatus != 403) {
            return upstreamStatus;
        }
        return 502;
    }

    static String detail(ObjectMapper objectMapper, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<>() {
            });
            String fieldErrors = fieldErrorsMessage(parsed.get("fieldErrors"));
            if (fieldErrors != null) {
                return fieldErrors;
            }
            String message = text(parsed.get("message"));
            if (message != null) {
                return message;
            }
            String detail = text(parsed.get("detail"));
            if (detail != null) {
                return detail;
            }
            return text(parsed.get("title"));
        } catch (Exception ignored) {
            return body.length() > 300 ? body.substring(0, 300) : body;
        }
    }

    private static String fieldErrorsMessage(Object fieldErrors) {
        if (!(fieldErrors instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String message = text(map.get("message"));
            if (message == null) {
                continue;
            }
            String field = text(map.get("field"));
            parts.add(field == null ? message : field + ": " + message);
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
