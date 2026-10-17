package com.ael.algoryqrservice.qrservice.exception;



public class InvalidPaymentEventException extends RuntimeException {

    public InvalidPaymentEventException(String message) {
        super(message);
    }

    public InvalidPaymentEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
