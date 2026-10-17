package com.ael.algoryqrservice.paymentservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.callback")
public record PaymentCallbackProperties(
        String publicUrl,
        String checkoutFormPublicUrl,
        String frontendRedirectUrl,
        String cardVerificationFrontendRedirectUrl
) {
    public PaymentCallbackProperties {
        if (frontendRedirectUrl == null || frontendRedirectUrl.isBlank()) {
            frontendRedirectUrl = "http://localhost:3000/payment/result";
        }
        if (checkoutFormPublicUrl == null || checkoutFormPublicUrl.isBlank()) {
            checkoutFormPublicUrl = "http://localhost:8080/payments/paytr/notification";
        }
        if (cardVerificationFrontendRedirectUrl == null || cardVerificationFrontendRedirectUrl.isBlank()) {
            cardVerificationFrontendRedirectUrl = "http://localhost:3000/dashboard/hesabim/kayitli-kartlarim";
        }
    }
}
