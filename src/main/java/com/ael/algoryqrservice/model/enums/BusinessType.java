package com.ael.algoryqrservice.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum BusinessType {
    RESTAURANT("restaurant"),
    CAFE("cafe"),
    HOTEL("hotel"),
    OTHER("other");

    private final String code;

    BusinessType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static BusinessType fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (BusinessType type : values()) {
            if (type.code.equals(normalized) || type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown business type: " + raw);
    }
}
