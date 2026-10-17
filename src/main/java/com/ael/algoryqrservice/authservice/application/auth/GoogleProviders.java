package com.ael.algoryqrservice.authservice.application.auth;

public final class GoogleProviders {

    public static final String GOOGLE = "GOOGLE";
    public static final String MOBILE_GOOGLE = "MOBILE_GOOGLE";
    public static final String BASIC = "BASIC";

    private GoogleProviders() {
    }

    public static boolean isGoogle(String provider) {
        return GOOGLE.equalsIgnoreCase(provider) || MOBILE_GOOGLE.equalsIgnoreCase(provider);
    }
}
