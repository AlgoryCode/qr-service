package com.ael.algoryqrservice.exception;

public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String message) {
        this(null, message);
    }

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
