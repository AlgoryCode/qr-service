package com.ael.algoryqrservice.paymentservice.domain.exception;

public class SubscriptionNotFoundException extends DomainException {

    public SubscriptionNotFoundException(String id) {
        super("SUBSCRIPTION_NOT_FOUND", "Subscription not found: " + id);
    }
}
