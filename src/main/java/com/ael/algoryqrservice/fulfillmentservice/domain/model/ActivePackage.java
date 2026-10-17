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
        Instant updatedAt
) {
}
