package com.ael.algoryqrservice.fulfillmentservice.domain.model;

/** What kind of package a merchant is on right now; drives the access decision and the lifecycle job. */
public enum SubscriptionKind {
    /** Time-limited full trial; drops to {@link #FREE} when it ends. */
    TRIAL,
    /** Paid package; on missed payment it enters grace, then drops to {@link #FREE}. */
    PAID,
    /** Permanent free tier; never expires. */
    FREE,
    /** Package granted by an operator or a demo flow; drops to {@link #FREE} when it ends. */
    GRANT
}
