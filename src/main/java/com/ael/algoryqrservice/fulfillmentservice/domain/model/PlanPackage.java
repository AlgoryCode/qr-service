package com.ael.algoryqrservice.fulfillmentservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PlanPackage(
        Long id,
        String code,
        String name,
        String description,
        PackageKind kind,
        List<String> features,
        BigDecimal price,
        BigDecimal yearlyPrice,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        String currency,
        boolean active,
        Integer validityDays,
        Integer priority,
        boolean purchasable,
        boolean systemManaged,
        List<PlanPackageItem> items,
        Instant createdAt,
        Instant updatedAt
) {
}
