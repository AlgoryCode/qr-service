package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageKind;

import java.math.BigDecimal;
import java.util.List;

public record PackageResponse(
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
        List<PackageItemResponse> items
) {
}
