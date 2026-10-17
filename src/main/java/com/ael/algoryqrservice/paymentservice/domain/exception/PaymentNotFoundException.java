package com.ael.algoryqrservice.paymentservice.domain.exception;

public class PaymentNotFoundException extends DomainException {

    public PaymentNotFoundException(String conversationId) {
        super("PAYMENT_NOT_FOUND", "Payment not found for conversationId: " + conversationId);
    }
}
