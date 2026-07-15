package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PackageCode;
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
    private PackageCode code;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private boolean active;
    private Integer validityDays;
    private List<PaymentMode> allowedPaymentModes;
    private List<Integer> allowedInstallments;
    private List<InstallmentOptionResponse> installmentOptions;
    private List<PlanPackageItemResponse> items;
    private LocalDateTime createdAt;
}
