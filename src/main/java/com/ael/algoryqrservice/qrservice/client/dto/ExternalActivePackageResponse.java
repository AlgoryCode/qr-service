package com.ael.algoryqrservice.qrservice.client.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExternalActivePackageResponse(
        Long id,
        Long merchantId,
        Long fulfillmentId,
        Long purchaseId,
        Long packageId,
        String packageCode,
        String packageName,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        Instant activatedAt,
        Instant updatedAt,
        List<String> products,
        List<String> scopes,
        /** TRIAL, PAID, FREE or GRANT; see SubscriptionKind. */
        String kind,
        Instant graceEndsAt
) {

    public ExternalActivePackageResponse(
            Long id, Long merchantId, Long fulfillmentId, Long purchaseId, Long packageId,
            String packageCode, String packageName, LocalDate periodStart, LocalDate periodEnd,
            String status, Instant activatedAt, Instant updatedAt, List<String> products, List<String> scopes
    ) {
        this(id, merchantId, fulfillmentId, purchaseId, packageId, packageCode, packageName, periodStart,
                periodEnd, status, activatedAt, updatedAt, products, scopes, null, null);
    }

    /**
     * Whether the package's features are on right now: an ACTIVE package within its period, or a
     * paid package in its payment grace period. The single rule every access check uses.
     */
    public boolean isOpen() {
        if ("GRACE_PERIOD".equals(status)) {
            return graceEndsAt == null || graceEndsAt.isAfter(Instant.now());
        }
        if (!"ACTIVE".equals(status)) {
            return false;
        }
        return periodEnd == null || !periodEnd.isBefore(LocalDate.now(com.ael.algoryqrservice.qrservice.util.AppTime.ZONE));
    }

    public boolean inGrace() {
        return "GRACE_PERIOD".equals(status) && isOpen();
    }
}
