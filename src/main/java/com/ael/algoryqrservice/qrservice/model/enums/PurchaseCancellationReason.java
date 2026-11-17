package com.ael.algoryqrservice.qrservice.model.enums;

public enum PurchaseCancellationReason {
    PAYMENT_TIMEOUT,
    MANUAL,
    /** A second package was paid while one was already active; the payment is refunded. */
    DUPLICATE_PACKAGE
}
