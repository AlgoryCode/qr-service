package com.ael.algoryqrservice.fulfillmentservice.domain.model;

import java.time.Instant;
import java.time.LocalDate;

public record ActivePackage(
        Long id,
        Long merchantId,
        Long fulfillmentId,
        Long purchaseId,
        Long packageId,
        String packageCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        PackageStatus status,
        Instant activatedAt,
        Instant updatedAt,
        SubscriptionKind kind,
        /** When a missed payment's grace period ends; null unless {@code status} is GRACE_PERIOD. */
        Instant graceEndsAt
) {

    /** Whether the merchant may use the package right now (paid, trial, free or in payment grace). */
    public boolean grantsAccess() {
        return status != null && status.grantsAccess();
    }

    public ActivePackage withStatus(PackageStatus newStatus, Instant newGraceEndsAt) {
        return new ActivePackage(id, merchantId, fulfillmentId, purchaseId, packageId, packageCode,
                periodStart, periodEnd, newStatus, activatedAt, Instant.now(), kind, newGraceEndsAt);
    }
}
