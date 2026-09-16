package com.ael.algoryqrservice.model.enums;

public enum AuthProvider {
    BASIC,
    GOOGLE,
    MOBILE_GOOGLE;

    public boolean isGoogle() {
        return this == GOOGLE || this == MOBILE_GOOGLE;
    }
}
