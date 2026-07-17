package com.ael.algoryqrservice.model.enums;

import java.util.Locale;

public enum GoogleAuthErrorCode {
    ACCESS_DENIED,
    ACCOUNT_EXISTS,
    ACCOUNT_NOT_FOUND,
    EMAIL_NOT_VERIFIED,
    GOOGLE_AUTH_FAILED,
    INVALID_INTENT,
    INVALID_TICKET,
    OAUTH_FAILED,
    PROVIDER_CONFLICT,
    REGISTRATION_FAILED,
    TICKET_EXPIRED,
    TICKET_USED,
    UPSTREAM_UNAVAILABLE;

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
        if (normalized.contains("doğrulanmış google e-posta")
                || normalized.contains("dogrulanmis google e-posta")) {
            return EMAIL_NOT_VERIFIED;
        }
        if (normalized.contains("zaten kayıtlı") || normalized.contains("zaten kayitli")) {
            return ACCOUNT_EXISTS;
        }
        if (normalized.contains("farklı bir giriş") || normalized.contains("provider conflict")) {
            return PROVIDER_CONFLICT;
        }
        if (normalized.contains("kimlik doğrulama oturumu")
                || normalized.contains("kimlik doğrulama amacı")) {
            return OAUTH_FAILED;
        }
        return GOOGLE_AUTH_FAILED;
    }
}
