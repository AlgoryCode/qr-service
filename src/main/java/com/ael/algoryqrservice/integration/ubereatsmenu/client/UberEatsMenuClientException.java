package com.ael.algoryqrservice.integration.ubereatsmenu.client;

public class UberEatsMenuClientException extends RuntimeException {

    private final int statusCode;
    private final boolean retryable;

    public UberEatsMenuClientException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public UberEatsMenuClientException(String message, int statusCode, boolean retryable, Throwable cause) {
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
