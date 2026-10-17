package com.ael.algoryqrservice.paymentservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.token")
public record PaymentTokenProperties(String encryptionKey) {
}
