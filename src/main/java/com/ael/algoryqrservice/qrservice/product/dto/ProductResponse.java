package com.ael.algoryqrservice.qrservice.product.dto;

import com.ael.algoryqrservice.qrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.qrservice.model.enums.ProductType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String scopeCode;
    private ProductType typeId;
    private String featureCode;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private boolean countable;
    private boolean consumable;
    private boolean addonPurchasable;
    private ProductBillingType billingType;
    private boolean active;
    private LocalDateTime createdAt;
}
