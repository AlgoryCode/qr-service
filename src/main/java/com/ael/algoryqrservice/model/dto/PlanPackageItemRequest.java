package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlanPackageItemRequest {

    @NotNull(message = "Urun id zorunludur")
    private Long productId;

    @Min(value = 0, message = "Miktar 0 veya daha buyuk olmalidir")
    private Integer quantity;

    private Boolean unlimited;

    public boolean resolvedUnlimited() {
        return Boolean.TRUE.equals(unlimited);
    }
}
