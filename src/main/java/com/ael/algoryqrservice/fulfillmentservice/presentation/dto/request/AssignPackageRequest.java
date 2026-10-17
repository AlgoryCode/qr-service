package com.ael.algoryqrservice.fulfillmentservice.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AssignPackageRequest(
        @NotBlank String packageCode,
        @Min(1) int periodDays
) {
}
