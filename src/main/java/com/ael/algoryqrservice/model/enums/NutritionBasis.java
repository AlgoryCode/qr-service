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
        return NutritionBasis.valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
