package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.BusinessType;
import com.ael.algoryqrservice.model.enums.UsagePurpose;
import jakarta.validation.constraints.NotNull;

public record OnboardingPackageStartRequest(
        Long packageId,
        @NotNull BusinessType businessType,
        @NotNull UsagePurpose usagePurpose
) {
}
