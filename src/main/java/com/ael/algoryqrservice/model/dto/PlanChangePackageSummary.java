package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PlanChangePackageSummary {
    private Long id;
    private String code;
    private String name;
    private BigDecimal price;
    private BigDecimal monthlyDiscount;
    private BigDecimal yearlyPrice;
    private BigDecimal yearlyDiscount;
    private BigDecimal effectiveMonthlyPrice;
    private BigDecimal effectiveYearlyPrice;
    private String currency;
    private Integer validityDays;
    private List<String> features;
}
