package com.ael.algoryqrservice.demo;

import java.util.Locale;

public final class DemoTrialAccountSupport {

    public static final int TRIAL_DAYS = 15;
    public static final String TRIAL_PACKAGE_CODE = "ULTIMATE_TRIAL_PACKAGE";
    public static final String TRIAL_PACKAGE_DISPLAY_NAME = "Algory Ultimate Deneme Surumu";
    public static final String TRIAL_ACCOUNT_LABEL = "AlgoryQR Deneme Surumu";
    public static final String EMAIL_DOMAIN = "@demo.algoryqr.local";

    private DemoTrialAccountSupport() {
    }

    public static boolean isDemoEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return email.trim().toLowerCase(Locale.ROOT).endsWith(EMAIL_DOMAIN);
    }
}
