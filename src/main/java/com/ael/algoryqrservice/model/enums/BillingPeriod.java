package com.ael.algoryqrservice.model.enums;

public enum BillingPeriod {
    MONTHLY,
    YEARLY;

    public int intervalMonths() {
        return this == YEARLY ? 12 : 1;
    }
}
