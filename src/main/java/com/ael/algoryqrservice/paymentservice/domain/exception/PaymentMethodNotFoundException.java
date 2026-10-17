package com.ael.algoryqrservice.paymentservice.domain.exception;

public class PaymentMethodNotFoundException extends DomainException {

    public PaymentMethodNotFoundException(Long id) {
        super("PAYMENT_METHOD_NOT_FOUND", "Payment method not found: " + id);
    }
}
