package com.ael.algoryqrservice.qrservice.billing.admin;

import com.ael.algoryqrservice.qrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.qrservice.model.enums.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Admin catalog API: products are the sellable rights, packages bundle them with quantities. */
public final class AdminCatalogDtos {

    /** Upper-snake identifier; never changes once created because entitlements and code refer to it. */
    public static final String CODE_PATTERN = "^[A-Z][A-Z0-9_]{1,63}$";

    private AdminCatalogDtos() {
    }

    public record ProductRequest(
            @NotBlank @Pattern(regexp = CODE_PATTERN, message = "Kod büyük harf, rakam ve _ içermeli") String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            @NotNull ProductType type,
            @Pattern(regexp = CODE_PATTERN, message = "Hak kodu büyük harf, rakam ve _ içermeli") String featureCode,
            @Size(max = 64) String scopeCode,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal vatRate,
            @NotNull ProductBillingType billingType,
            boolean consumable,
            boolean addonPurchasable,
            boolean requiresCountSync,
            boolean active
    ) {
    }

    public record ProductResponse(
            Long id,
            String code,
            String name,
            String description,
            ProductType type,
            String featureCode,
            String scopeCode,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            BigDecimal priceWithVat,
            ProductBillingType billingType,
            boolean consumable,
            boolean addonPurchasable,
            boolean requiresCountSync,
            boolean active,
            long packageCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PackageItemRequest(
            @NotNull Long productId,
            @Min(0) Integer quantity,
            boolean unlimited
    ) {
    }

    public record PackageRequest(
            @NotBlank @Pattern(regexp = CODE_PATTERN, message = "Kod büyük harf, rakam ve _ içermeli") String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            List<@NotBlank @Size(max = 200) String> features,
            @DecimalMin("0.00") BigDecimal monthlyDiscount,
            @DecimalMin("0.00") BigDecimal yearlyPrice,
            @DecimalMin("0.00") BigDecimal yearlyDiscount,
            @Size(min = 3, max = 3) String currency,
            @NotNull @Min(1) Integer validityDays,
            Integer priority,
            boolean purchasable,
            boolean active,
            @NotEmpty List<@Valid PackageItemRequest> items
    ) {
    }

    public record PackageItemResponse(
            Long id,
            Long productId,
            String productCode,
            String productName,
            String featureCode,
            ProductType productType,
            Integer quantity,
            boolean unlimited,
            BigDecimal lineTotal
    ) {
    }

    public record PackageResponse(
            Long id,
            String code,
            String name,
            String description,
            List<String> features,
            BigDecimal subtotal,
            BigDecimal vatAmount,
            BigDecimal price,
            BigDecimal monthlyDiscount,
            BigDecimal effectiveMonthlyPrice,
            BigDecimal yearlyPrice,
            BigDecimal yearlyDiscount,
            BigDecimal effectiveYearlyPrice,
            String currency,
            Integer validityDays,
            Integer priority,
            boolean purchasable,
            boolean active,
            boolean systemManaged,
            List<PackageItemResponse> items,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
