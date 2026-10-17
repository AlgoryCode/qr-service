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
    REVOKED
}
