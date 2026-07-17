package com.ael.algoryqrservice.security;

public final class GoogleOAuthPaths {

    public static final String AUTHORIZE = "/google-auth/authorize";
    public static final String REDEEM = "/google-auth/redeem";
    public static final String CALLBACK = "/google-auth/callback";
    public static final String LEGACY_CALLBACK = "/auth/google/callback";
    public static final String AUTHORIZATION = "/oauth2/authorization/google";

    private GoogleOAuthPaths() {
    }
}
