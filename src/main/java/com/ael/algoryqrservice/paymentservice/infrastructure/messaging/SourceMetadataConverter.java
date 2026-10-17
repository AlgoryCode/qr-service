package com.ael.algoryqrservice.paymentservice.infrastructure.messaging;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SourceMetadataConverter {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "cardnumber",
            "pan",
            "cvc",
            "cvv",
            "expiremonth",
            "expireyear",
            "cardholdername",
            "cardtoken",
            "carduserkey"
    );

    private final JsonMapper jsonMapper;

    public SourceMetadataConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(sanitizeMap(metadata));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid sourceMetadata", exception);
        }
    }

    public Map<String, Object> fromJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return jsonMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid stored sourceMetadata", exception);
        }
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String stringKey = String.valueOf(key);
            if (!SENSITIVE_KEYS.contains(normalizeKey(stringKey))) {
                sanitized.put(stringKey, sanitizeValue(value));
            }
        });
        return sanitized;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            return sanitizeMap(nestedMap);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    private String normalizeKey(String key) {
        return key.replace("_", "").replace("-", "").toLowerCase();
    }
}
