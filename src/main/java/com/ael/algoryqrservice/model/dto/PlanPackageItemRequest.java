package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlanPackageItemRequest {

    @NotNull(message = "Ürün id zorunludur")
    private Long productId;

    @NotNull(message = "Miktar zorunludur")
    @Min(value = 1, message = "Miktar en az 1 olmalıdır")
    private Integer quantity;
}
