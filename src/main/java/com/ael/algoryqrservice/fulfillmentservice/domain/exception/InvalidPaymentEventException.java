package com.ael.algoryqrservice.fulfillmentservice.domain.exception;

public class InvalidPaymentEventException extends DomainException {

    public InvalidPaymentEventException(String message) {
        super("INVALID_PAYMENT_EVENT", message);
    }

    public InvalidPaymentEventException(String message, Throwable cause) {
        super("INVALID_PAYMENT_EVENT", message, cause);
    }
}
