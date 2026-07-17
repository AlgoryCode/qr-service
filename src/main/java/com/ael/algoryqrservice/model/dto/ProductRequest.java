package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProductRequest {

    @NotBlank(message = "Ürün kodu zorunludur")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Ürün kodu uppercase snake_case olmalıdır")
    private String code;

    @NotBlank(message = "Ürün adı zorunludur")
    private String name;

    private String description;

    @NotBlank(message = "Scope kodu zorunludur")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Scope kodu uppercase snake_case olmalıdır")
    private String scopeCode;

    @NotNull(message = "Tüketilebilirlik zorunludur")
    private Boolean consumable;

    @NotNull(message = "Aktiflik durumu zorunludur")
    private Boolean active;
}
