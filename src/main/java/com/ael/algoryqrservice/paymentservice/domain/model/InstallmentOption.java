package com.ael.algoryqrservice.paymentservice.domain.model;

import java.math.BigDecimal;

public record InstallmentOption(
        int installmentCount,
        BigDecimal installmentAmount,
        BigDecimal totalAmount
) {
}
