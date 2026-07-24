package com.ael.algoryqrservice.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlanPackageRequest {

    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Paket kodu uppercase snake_case olmalidir")
    private String code;

    @NotBlank(message = "Paket adi zorunludur")
    private String name;

    private String description;

    private List<String> features;

    @PositiveOrZero(message = "Fiyat 0 veya daha buyuk olmalidir")
    private BigDecimal price;

    @PositiveOrZero(message = "Aylik indirim 0 veya daha buyuk olmalidir")
    private BigDecimal monthlyDiscount;

    @PositiveOrZero(message = "Yillik fiyat 0 veya daha buyuk olmalidir")
    private BigDecimal yearlyPrice;

    @PositiveOrZero(message = "Yillik indirim 0 veya daha buyuk olmalidir")
    private BigDecimal yearlyDiscount;

    private String currency;

    private Boolean active;

    @Min(value = 1, message = "Gecerlilik suresi en az 1 gun olmalidir")
    private Integer validityDays;

    private Integer priority;

    private Boolean purchasable;

    private Boolean trialEligible;

    @Valid
    private List<PlanPackageItemRequest> items;

    public boolean resolvedActive() {
        return active == null || Boolean.TRUE.equals(active);
    }

    public boolean resolvedPurchasable() {
        return Boolean.TRUE.equals(purchasable);
    }

    public boolean resolvedTrialEligible() {
        return Boolean.TRUE.equals(trialEligible);
    }

    public int resolvedPriority() {
        return priority == null ? 0 : priority;
    }

    public int resolvedValidityDays() {
        return validityDays == null ? 30 : validityDays;
    }
}
