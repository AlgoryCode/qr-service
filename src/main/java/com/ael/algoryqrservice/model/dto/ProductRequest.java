package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.ProductCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductRequest {

    @NotNull(message = "Ürün kodu zorunludur")
    private ProductCode code;

    @NotBlank(message = "Ürün adı zorunludur")
    private String name;

    private String description;

    @NotNull(message = "Aktiflik durumu zorunludur")
    private Boolean active;
}
