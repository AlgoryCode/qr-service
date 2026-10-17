package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record EntitlementQuantityRequest(
        @NotBlank String productCode,
        @Min(1) int quantity
) {
}
