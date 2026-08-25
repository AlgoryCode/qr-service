package com.ael.algoryqrservice.util;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.BillingPeriod;

/**
 * Derives the billing period of a purchase, falling back to its stored interval for legacy rows
 * that were written before {@code billingPeriod} existed.
 */
public final class BillingPeriodResolver {

    private static final int YEARLY_INTERVAL_MONTHS = 12;

    private BillingPeriodResolver() {
    }

    public static BillingPeriod resolve(Purchase purchase) {
        if (purchase.getBillingPeriod() != null) {
            return purchase.getBillingPeriod();
        }
        Integer intervalMonths = purchase.getBillingIntervalMonths();
        if (intervalMonths == null) {
            return BillingPeriod.MONTHLY;
        }
        if (intervalMonths >= YEARLY_INTERVAL_MONTHS) {
            return BillingPeriod.YEARLY;
        }
        return intervalMonths <= 0 ? BillingPeriod.ONE_TIME : BillingPeriod.MONTHLY;
    }
}
