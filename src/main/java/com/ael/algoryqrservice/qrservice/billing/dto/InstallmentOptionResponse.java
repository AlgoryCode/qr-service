package com.ael.algoryqrservice.qrservice.billing.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class InstallmentOptionResponse {
    Integer installmentCount;
    BigDecimal monthlyAmount;
    BigDecimal totalAmount;
}
