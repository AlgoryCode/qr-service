package com.ael.algoryqrservice.paymentservice.domain.exception;

public class PaymentProcessingException extends DomainException {

    public PaymentProcessingException(String message) {
        super("PAYMENT_PROCESSING_ERROR", message);
    }

    public PaymentProcessingException(String errorCode, String message) {
        super(errorCode, message);
    }
}
