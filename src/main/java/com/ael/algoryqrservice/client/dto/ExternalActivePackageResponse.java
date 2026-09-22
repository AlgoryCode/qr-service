package com.ael.algoryqrservice.client.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ExternalActivePackageResponse(
        Long id,
        Long userId,
        Long fulfillmentId,
        Long purchaseId,
        Long packageId,
        String packageCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        Instant activatedAt,
        Instant updatedAt
) {
}
