package com.ael.algoryqrservice.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SellablePackageComposeRequest {

    @NotBlank(message = "Paket adi zorunludur")
    private String packageName;

    private String packageCode;

    private String description;

    private List<String> features;

    private String currency;

    @Min(value = 1, message = "Gecerlilik suresi en az 1 gun olmalidir")
    private Integer validityDays;

    private Integer priority;

    private Boolean trialEligible;

    @NotEmpty(message = "En az bir urun gerekli")
    @Valid
    private List<ComposeItemRequest> items;

    public int resolvedValidityDays() {
        return validityDays == null ? 30 : validityDays;
    }

    @Data
    public static class ComposeItemRequest {
        private Long productId;
        private String productName;
        private String productDescription;
        private Boolean countable;
        private Integer quantity;
        private Boolean unlimited;

        @PositiveOrZero
        private BigDecimal unitPrice;

        @DecimalMin("0.00")
        @DecimalMax("100.00")
        private BigDecimal vatRate;

        public boolean resolvedUnlimited() {
            return Boolean.TRUE.equals(unlimited);
        }

        public boolean resolvedCountable() {
            return countable == null || Boolean.TRUE.equals(countable);
        }
    }
}
