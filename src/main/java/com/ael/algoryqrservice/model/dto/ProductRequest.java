package com.ael.algoryqrservice.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Urun kodu uppercase snake_case olmalidir")
    private String code;

    @NotBlank(message = "Urun adi zorunludur")
    private String name;

    private String description;

    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Scope kodu uppercase snake_case olmalidir")
    private String scopeCode;

    @PositiveOrZero(message = "Birim fiyat 0 veya daha buyuk olmalidir")
    private BigDecimal unitPrice;

    @DecimalMin(value = "0.00", message = "KDV orani 0 veya daha buyuk olmalidir")
    @DecimalMax(value = "100.00", message = "KDV orani en fazla 100 olabilir")
    private BigDecimal vatRate;

    @JsonAlias({"consumable"})
    private Boolean countable;

    private Boolean active;

    public boolean resolvedCountable() {
        return countable == null || Boolean.TRUE.equals(countable);
    }

    public boolean resolvedActive() {
        return active == null || Boolean.TRUE.equals(active);
    }

    public BigDecimal resolvedUnitPrice() {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice;
    }

    public BigDecimal resolvedVatRate() {
        return vatRate == null ? new BigDecimal("20.00") : vatRate;
    }
}
