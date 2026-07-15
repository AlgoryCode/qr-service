package com.ael.algoryqrservice.model.enums;

import com.ael.algoryqrservice.exception.BadRequestException;

import java.util.Locale;

public enum GoogleAuthIntent {
    LOGIN,
    REGISTER;

    public static GoogleAuthIntent from(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Google kimlik doğrulama amacı zorunludur");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Google kimlik doğrulama amacı geçersiz");
        }
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
