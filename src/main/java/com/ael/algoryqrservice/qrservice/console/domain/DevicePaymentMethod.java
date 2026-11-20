package com.ael.algoryqrservice.qrservice.console.domain;

/** How a finished session (or a balance top-up) was paid. BALANCE spends the customer's prepaid balance. */
public enum DevicePaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    BALANCE;

    public String label() {
        return switch (this) {
            case CASH -> "Nakit";
            case CARD -> "Kart";
            case TRANSFER -> "Havale";
            case BALANCE -> "Bakiye";
        };
    }
}
