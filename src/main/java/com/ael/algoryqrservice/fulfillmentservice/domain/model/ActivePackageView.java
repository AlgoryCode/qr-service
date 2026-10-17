package com.ael.algoryqrservice.fulfillmentservice.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ActivePackageView(
        Long id,
        Long merchantId,
        Long fulfillmentId,
        Long purchaseId,
        Long packageId,
        String packageCode,
        String packageName,
        LocalDate periodStart,
        LocalDate periodEnd,
        PackageStatus status,
        Instant activatedAt,
        Instant updatedAt,
        List<String> products,
        List<String> scopes,
        List<UserEntitlement> entitlements
) {
}
