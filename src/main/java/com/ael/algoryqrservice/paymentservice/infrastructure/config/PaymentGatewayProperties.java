package com.ael.algoryqrservice.paymentservice.infrastructure.config;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.gateway")
public record PaymentGatewayProperties(PaymentProvider defaultProvider) {

    public PaymentGatewayProperties {
        if (defaultProvider == null) {
            defaultProvider = PaymentProvider.PAYTR;
        }
    }
}
