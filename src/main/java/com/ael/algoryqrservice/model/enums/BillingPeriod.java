package com.ael.algoryqrservice.model.enums;

public enum BillingPeriod {
    MONTHLY,
    YEARLY,
    ONE_TIME;

    public int intervalMonths() {
        return switch (this) {
            case YEARLY -> 12;
            case MONTHLY -> 1;
            case ONE_TIME -> 0;
        };
    }
}
