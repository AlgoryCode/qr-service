package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlanPackageItemResponse {

    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private Integer quantity;
    private boolean unlimited;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private Integer billableQuantity;
    private BigDecimal lineSubtotal;
    private BigDecimal lineVat;
    private BigDecimal lineTotal;
}
