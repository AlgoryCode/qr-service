package com.ael.algoryqrservice.paymentservice.domain.model;

public enum PaymentProvider {
    /**
     * Legacy rows may still store IYZICO; new payments must use PAYTR.
     */
    IYZICO,
    PAYTR
}
