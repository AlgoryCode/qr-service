package com.ael.algoryqrservice.authservice.exception;

import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException {

    /** The refresh token's session expired, or the token is unknown. */
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    /** The session was signed out, from this device or another. */
    public static final String SESSION_REVOKED = "SESSION_REVOKED";
    /** An already rotated refresh token came back after its grace window; the session was revoked. */
    public static final String SESSION_REUSED = "SESSION_REUSED";

    private final String code;

    public UnauthorizedException(String message) {
        super(message);
        this.code = null;
    }

    public UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }
}
