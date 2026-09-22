package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.ProductBillingType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * An optional module the buyer may attach to this package at checkout, resolved from
 * {@code tbl_plan_package_addon}.
 */
@Data
@Builder
public class PlanPackageModuleResponse {

    private Long productId;
    private String productCode;
    private String productName;
    private String description;
    private String featureCode;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private ProductBillingType billingType;
    private boolean consumable;
}
