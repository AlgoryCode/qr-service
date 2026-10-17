package com.ael.algoryqrservice.authservice.application.auth;

import java.util.Locale;

public enum GoogleAuthErrorCode {
    ACCESS_DENIED,
    ACCOUNT_EXISTS,
    ACCOUNT_NOT_FOUND,
    EMAIL_NOT_VERIFIED,
    GOOGLE_AUTH_FAILED,
    INVALID_INTENT,
    INVALID_CLIENT,
    OAUTH_FAILED,
    PROVIDER_CONFLICT;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GoogleAuthErrorCode fromMessage(String message) {
        if (message == null || message.isBlank()) {
            return GOOGLE_AUTH_FAILED;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("kayıtlı değil") || normalized.contains("kayitli degil")) {
            return ACCOUNT_NOT_FOUND;
        }
        if (normalized.contains("doğrulanmış") || normalized.contains("dogrulanmis")) {
            return EMAIL_NOT_VERIFIED;
        }
        if (normalized.contains("zaten kayıtlı") || normalized.contains("zaten kayitli")) {
            return ACCOUNT_EXISTS;
        }
        if (normalized.contains("e-posta/şifre") || normalized.contains("e-posta/sifre")
                || normalized.contains("farklı bir giriş") || normalized.contains("farkli bir giris")) {
            return PROVIDER_CONFLICT;
        }
        return GOOGLE_AUTH_FAILED;
    }
}
