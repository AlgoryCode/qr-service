package com.ael.algoryqrservice.integration.ubereats.client;

public class UberEatsClientException extends RuntimeException {

    private final Integer httpStatus;

    public UberEatsClientException(String message) {
        this(message, null, null);
    }

    public UberEatsClientException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public UberEatsClientException(String message, Throwable cause, Integer httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
