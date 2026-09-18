package com.ael.algoryqrservice.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum UsagePurpose {
    DIGITAL_MENU("digital_menu"),
    WAITER_ORDERS("waiter_orders"),
    REPORTS("reports"),
    EXPLORE_ALL("explore_all");

    private final String code;

    UsagePurpose(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static UsagePurpose fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (UsagePurpose purpose : values()) {
            if (purpose.code.equals(normalized) || purpose.name().equalsIgnoreCase(raw.trim())) {
                return purpose;
            }
        }
        throw new IllegalArgumentException("Unknown usage purpose: " + raw);
    }
}
