package com.ael.algoryqrservice.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NutritionBasis {
    PER_100G,
    PER_100ML;

    @JsonCreator
    public static NutritionBasis from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if ("PER_100ML".equals(normalized)) {
            return PER_100ML;
        }
        return PER_100G;
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
