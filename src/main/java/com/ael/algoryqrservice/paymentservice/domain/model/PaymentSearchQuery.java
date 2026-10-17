package com.ael.algoryqrservice.paymentservice.domain.model;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PaymentSearchQuery(
        String query,
        PaymentStatus status,
        PaymentType paymentType,
        PaymentStyle paymentStyle,
        String accountId,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        Boolean verificationOnly
) {
}
