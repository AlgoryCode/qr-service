package com.ael.algoryqrservice.fulfillmentservice.domain.model;

public enum PackageStatus {
    PENDING,
    ACTIVE,
    PAST_DUE,
    GRACE_PERIOD,
    SUSPENDED,
    CANCELLED,
    EXPIRED,
    SUPERSEDED,
    REVOKED;

    /** ACTIVE and GRACE_PERIOD keep the merchant's features on; every other status is closed. */
    public boolean grantsAccess() {
        return this == ACTIVE || this == GRACE_PERIOD;
    }
}
