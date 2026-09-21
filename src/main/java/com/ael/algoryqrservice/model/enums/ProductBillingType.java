package com.ael.algoryqrservice.model.enums;

/**
 * Decides how a module line keeps being billed once it is attached to a subscription.
 * RECURRING lines are part of every renewal charge, ONE_TIME lines are only paid for
 * the period they were bought in.
 */
public enum ProductBillingType {
    RECURRING,
    ONE_TIME
}
