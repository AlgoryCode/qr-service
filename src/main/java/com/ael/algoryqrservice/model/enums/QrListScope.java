package com.ael.algoryqrservice.model.enums;

public enum QrListScope {
    ALL,
    CURRENT,
    LEGACY;

    public static QrListScope from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return QrListScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ALL;
        }
    }
}
