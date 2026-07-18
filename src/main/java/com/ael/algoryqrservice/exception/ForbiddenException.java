package com.ael.algoryqrservice.exception;

import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {

    public static final String MENU_OWNER_PACKAGE_INACTIVE = "MENU_OWNER_PACKAGE_INACTIVE";

    private final String code;

    public ForbiddenException(String message) {
        super(message);
        this.code = null;
    }

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }
}
