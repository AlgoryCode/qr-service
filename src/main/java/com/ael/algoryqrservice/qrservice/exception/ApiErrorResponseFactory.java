package com.ael.algoryqrservice.qrservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class ApiErrorResponseFactory {

    private ApiErrorResponseFactory() {
    }

    public static ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> b));
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Doğrulama hatası");
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    public static ResponseEntity<Map<String, String>> message(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    public static ResponseEntity<Map<String, String>> messageWithCode(HttpStatus status, String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        if (code != null && !code.isBlank()) {
            body.put("code", code);
        }
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    public static ResponseEntity<Map<String, String>> internalError() {
        return message(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen bir sunucu hatası oluştu");
    }
}
