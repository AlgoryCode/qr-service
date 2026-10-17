package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.EntitlementSource;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductType;

import java.time.Instant;

public record EntitlementResponse(
        Long id,
        Long merchantId,
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
        String productCode
) {
}
