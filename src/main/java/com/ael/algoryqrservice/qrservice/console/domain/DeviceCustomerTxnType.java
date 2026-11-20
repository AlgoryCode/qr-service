package com.ael.algoryqrservice.qrservice.console.domain;

/**
 * TOPUP, SPEND and ADJUST move the paid balance. GIFT and REFERRAL move the bonus balance, which is never income
 * and is used up as a discount.
 */
public enum DeviceCustomerTxnType {
    TOPUP,
    SPEND,
    ADJUST,
    GIFT,
    REFERRAL
}
