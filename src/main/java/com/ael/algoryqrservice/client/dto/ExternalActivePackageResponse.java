package com.ael.algoryqrservice.client.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExternalActivePackageResponse(
        Long id,
        Long userId,
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
        List<String> scopes
) {
}
