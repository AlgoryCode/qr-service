package com.ael.algoryqrservice.qrservice.access;

import com.ael.algoryqrservice.qrservice.client.dto.ExternalActivePackageResponse;

/** Where the merchant stands with billing; the panel shows a banner per state. */
public enum BillingState {
    /** Never had a package: the panel offers the trial. */
    NONE,
    /** In the free full trial. */
    TRIALING,
    /** On a paid (or operator-granted) package in good standing. */
    ACTIVE,
    /** A renewal payment failed; features stay on until the grace period ends. */
    PAST_DUE,
    /** On the permanent free package. */
    FREE,
    /** Had a package that is now closed (only when the free package is unavailable). */
    CLOSED;

    public static BillingState of(ExternalActivePackageResponse active) {
        if (active == null || !active.isOpen()) {
            return CLOSED;
        }
        if (active.inGrace()) {
            return PAST_DUE;
        }
        if ("TRIAL".equals(active.kind())) {
            return TRIALING;
        }
        if ("FREE".equals(active.kind())) {
            return FREE;
        }
        return ACTIVE;
    }
}
