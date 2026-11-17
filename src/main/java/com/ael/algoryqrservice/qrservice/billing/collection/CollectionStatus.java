package com.ael.algoryqrservice.qrservice.billing.collection;

/**
 * Where collecting an unpaid debt stands. This is not the payment's own status: it is how far the
 * follow-up has gone, from waiting for the money to legal action.
 */
public enum CollectionStatus {
    /** Debt is new; the renewal may still go through or the merchant may pay on their own. */
    AWAITING_PAYMENT(false),
    /** The merchant was reminded (call, e-mail, SMS). */
    REMINDED(false),
    /** The grace time passed without payment. */
    UNPAID(false),
    /** A formal notice (ihtarname) was sent. */
    FORMAL_NOTICE(false),
    /** The merchant agreed to a payment plan. */
    PAYMENT_PLAN(false),
    /** Handed to legal follow-up (icra / avukat). */
    LEGAL_FOLLOW_UP(false),
    /** The debt was paid. */
    PAID(true),
    /** Given up on: cannot be collected. */
    WRITTEN_OFF(true),
    /** Opened by mistake or waived. */
    CANCELLED(true);

    private final boolean closed;

    CollectionStatus(boolean closed) {
        this.closed = closed;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Statuses the system itself may move: once a person has escalated, only a payment closes it automatically. */
    public boolean isEarlyStage() {
        return this == AWAITING_PAYMENT || this == REMINDED;
    }
}
