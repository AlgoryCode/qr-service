package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;

import java.util.Locale;
import java.util.regex.Pattern;

public final class DemoAccountSupport {

    public static final int DEMO_PACKAGE_VALIDITY_DAYS = 15;
    public static final String DEMO_PACKAGE_CODE = "ULTIMATE_TRIAL_PACKAGE";
    public static final String DEMO_PACKAGE_DISPLAY_NAME = "Algory Ultimate Deneme Surumu";
    public static final String DEMO_ACCOUNT_LABEL = "AlgoryQR Deneme Surumu";
    public static final String EMAIL_DOMAIN = "@demo.algoryqr.local";

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,31}$");

    private DemoAccountSupport() {
    }

    public static String normalizeUsername(String raw) {
        if (raw == null) {
            throw new BadRequestException("Kullanici adi zorunludur");
        }
        String username = raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BadRequestException(
                    "Kullanici adi 3-32 karakter olmali; kucuk harf, rakam, nokta, alt cizgi veya tire kullanin"
            );
        }
        return username;
    }

    public static String demoEmailForUsername(String username) {
        return username + EMAIL_DOMAIN;
    }
}
