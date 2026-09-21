package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One optional module the buyer added to the package in the cart. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseModuleLineRequest {

    @NotBlank(message = "Urun kodu zorunludur")
    private String productCode;

    @Min(value = 1, message = "Adet en az 1 olmalidir")
    @Max(value = 20, message = "Adet en fazla 20 olabilir")
    private int quantity = 1;

    public String resolvedProductCode() {
        return productCode == null ? null : productCode.trim().toUpperCase();
    }

    public int resolvedQuantity() {
        return Math.max(quantity, 1);
    }
}
