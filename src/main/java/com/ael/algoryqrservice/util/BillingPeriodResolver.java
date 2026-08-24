package com.ael.algoryqrservice.util;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.BillingPeriod;

public final class BillingPeriodResolver {

    private BillingPeriodResolver() {
    }

    public static BillingPeriod resolve(Purchase purchase) {
        if (purchase.getBillingPeriod() != null) {
            return purchase.getBillingPeriod();
        }
        if (purchase.getBillingIntervalMonths() != null && purchase.getBillingIntervalMonths() >= 12) {
            return BillingPeriod.YEARLY;
        }
        return BillingPeriod.MONTHLY;
    }
}
