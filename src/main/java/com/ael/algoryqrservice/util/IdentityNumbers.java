package com.ael.algoryqrservice.util;

public final class IdentityNumbers {
    public static final String DEFAULT = "11111111111";

    private IdentityNumbers() {
    }

    public static String firstOrDefault(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return DEFAULT;
    }
}
