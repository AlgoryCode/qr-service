package com.ael.algoryqrservice.paymentservice.presentation.dto.response;

public record PaymentMethodResponse(
        Long id,
        String alias,
        String brand,
        String family,
        String bank,
        String last4,
        boolean defaultMethod
) {
}
