package com.ael.algoryqrservice.exception;

public class PaymentServiceException extends RuntimeException {

    private final int status;

    public PaymentServiceException(String message) {
        this(message, 502);
    }

    public PaymentServiceException(String message, int status) {
        super(message);
        this.status = status <= 0 ? 502 : status;
    }

    public int getStatus() {
        return status;
    }
}
