package com.ael.algoryqrservice.model;

public enum UrlMode {
    ID,
    SLUG;

    public static UrlMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("urlMode zorunludur");
        }
        String normalized = raw.trim().toUpperCase();
        if ("ID".equals(normalized)) return ID;
        if ("SLUG".equals(normalized)) return SLUG;
        throw new IllegalArgumentException("Geçersiz urlMode: " + raw);
    }
}
