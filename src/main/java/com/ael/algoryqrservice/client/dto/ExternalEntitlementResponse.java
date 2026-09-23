package com.ael.algoryqrservice.client.dto;

import java.time.Instant;

public record ExternalEntitlementResponse(
        Long id,
        Long userId,
        Long fulfillmentId,
        Long purchaseId,
        Long productId,
        String productTypeId,
        String featureCode,
        String scopeCode,
        Integer quantity,
        boolean unlimited,
        Integer usedQuantity,
        String source,
        String status,
        Instant startsAt,
        Instant expiresAt,
        String productCode
) {
}
