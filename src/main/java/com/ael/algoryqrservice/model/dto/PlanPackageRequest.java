package com.ael.algoryqrservice.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlanPackageRequest {

    @NotBlank(message = "Paket kodu zorunludur")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Paket kodu uppercase snake_case olmalıdır")
    private String code;

    @NotBlank(message = "Paket adı zorunludur")
    private String name;

    private String description;

    @NotNull(message = "Fiyat zorunludur")
    @PositiveOrZero(message = "Fiyat 0 veya daha büyük olmalıdır")
    private BigDecimal price;

    private String currency;

    @NotNull(message = "Aktiflik durumu zorunludur")
    private Boolean active;

    @NotNull(message = "Geçerlilik süresi (gün) zorunludur")
    @Min(value = 1, message = "Geçerlilik süresi en az 1 gün olmalıdır")
    private Integer validityDays;

    @NotNull(message = "Öncelik zorunludur")
    @Min(value = 0, message = "Öncelik 0 veya daha büyük olmalıdır")
    private Integer priority;

    @NotNull(message = "Satın alınabilirlik zorunludur")
    private Boolean purchasable;

    @NotNull(message = "Deneme uygunluğu zorunludur")
    private Boolean trialEligible;

    @NotEmpty(message = "Paket en az bir ürün içermelidir")
    @Valid
    private List<PlanPackageItemRequest> items;
}
