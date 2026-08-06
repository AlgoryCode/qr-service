package com.ael.algoryqrservice.messaging.payment;

public final class PaymentEventTypes {

    public static final String PAYMENT_SUCCESS = "payment.success";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_SUBSCRIPTION_PAID = "payment.subscription.paid";
    public static final String PAYMENT_SUBSCRIPTION_FAILED = "payment.subscription.failed";
    public static final String PAYMENT_SUBSCRIPTION_PAST_DUE = "payment.subscription.past_due";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String PAYMENT_INSTALLMENT_PAID = "payment.installment.paid";
    public static final String PAYMENT_INSTALLMENT_FAILED = "payment.installment.failed";
    public static final String PAYMENT_INSTALLMENT_OVERDUE = "payment.installment.overdue";
    public static final String PAYMENT_CHARGEBACK = "payment.chargeback";
    public static final String SUBSCRIPTION_CANCELLED_AT_PERIOD_END = "subscription.cancelled_at_period_end";

    private PaymentEventTypes() {
    }
}
