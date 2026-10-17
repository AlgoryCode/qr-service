package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ActivePackageResponse(
        Long id,
        Long userId,
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
        List<String> scopes
) {
}
