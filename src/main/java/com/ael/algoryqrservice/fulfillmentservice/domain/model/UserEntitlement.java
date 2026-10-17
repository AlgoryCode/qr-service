package com.ael.algoryqrservice.fulfillmentservice.domain.model;

import java.time.Instant;

public record UserEntitlement(
        Long id,
        Long userId,
        Long fulfillmentId,
        Long purchaseId,
        Long productId,
        ProductType productTypeId,
        String featureCode,
        String scopeCode,
        Integer quantity,
        boolean unlimited,
        Integer usedQuantity,
        EntitlementSource source,
        PackageStatus status,
        Instant startsAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
