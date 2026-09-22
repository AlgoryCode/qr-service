package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddonCartLineRequest(
        @NotBlank(message = "Urun kodu zorunludur")
        String productCode,

        @Min(1)
        @Max(20)
        int quantity
) {
    public String resolvedProductCode() {
        return productCode == null ? "" : productCode.trim().toUpperCase();
    }

    public int resolvedQuantity() {
        return quantity < 1 ? 1 : quantity;
    }
}
