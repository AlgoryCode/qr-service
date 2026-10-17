package com.ael.algoryqrservice.paymentservice.presentation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubscriptionResponse(
        String id,
        Long paymentMethodId,
        String status,
        BigDecimal amount,
        String currency,
        int completedCycleCount,
        Integer totalCycleCount,
        LocalDateTime nextChargeAt
) {
}
