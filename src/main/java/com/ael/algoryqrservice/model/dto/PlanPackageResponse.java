package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PaymentMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PlanPackageResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private List<String> features;
    private BigDecimal subtotal;
    private BigDecimal vatAmount;
    private BigDecimal price;
    private String currency;
    private boolean active;
    private Integer validityDays;
    private Integer priority;
    private boolean purchasable;
    private boolean systemManaged;
    private boolean trialEligible;
    private List<PaymentMode> allowedPaymentModes;
    private List<Integer> allowedInstallments;
    private List<InstallmentOptionResponse> installmentOptions;
    private List<PlanPackageItemResponse> items;
    private LocalDateTime createdAt;
}
