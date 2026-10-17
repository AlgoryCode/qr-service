package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductType;

public record PackageItemResponse(
        Long productId,
        String productCode,
        String featureCode,
        String scopeCode,
        ProductType productType,
        Integer quantity,
        boolean unlimited
) {
}
