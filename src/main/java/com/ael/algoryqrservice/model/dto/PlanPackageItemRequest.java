package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlanPackageItemRequest {

    @NotNull(message = "Ürün id zorunludur")
    private Long productId;

    @NotNull(message = "Miktar zorunludur")
    @Min(value = 0, message = "Miktar 0 veya daha büyük olmalıdır")
    private Integer quantity;

    private Boolean unlimited = false;
}
