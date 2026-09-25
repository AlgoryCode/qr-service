package com.ael.algoryqrservice.security;

import java.util.Locale;

public final class LegacyGoogleAuthorizeRedirect {

    static final String STAGE_AUTH_BASE = "https://stage.auth.algorycode.com";
    static final String PROD_AUTH_BASE = "https://prod.auth.algorycode.com";
    static final String LOCAL_AUTH_BASE = "http://localhost:8080";
    static final String AUTHORIZE_PATH = "/api/v1/auth/google/authorize";

    private LegacyGoogleAuthorizeRedirect() {
    }

    public static String target(String configuredBase, String host, String query) {
        String location = resolveBase(configuredBase, host) + AUTHORIZE_PATH;
        if (query == null || query.isBlank() || query.indexOf('\r') >= 0 || query.indexOf('\n') >= 0) {
            return location;
        }
        return location + "?" + query;
    }

    private static String resolveBase(String configuredBase, String host) {
        if (configuredBase != null && !configuredBase.isBlank()) {
            return stripTrailingSlash(configuredBase.trim());
        }
        String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("stage.")) {
            return STAGE_AUTH_BASE;
        }
        if (normalizedHost.startsWith("prod.")) {
            return PROD_AUTH_BASE;
        }
        return LOCAL_AUTH_BASE;
    }

    private static String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
