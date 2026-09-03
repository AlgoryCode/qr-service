package com.ael.algoryqrservice.integration.ubereats.client;

public class UberEatsClientException extends RuntimeException {

    private final int statusCode;
    private final boolean retryable;

    public UberEatsClientException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public UberEatsClientException(String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
