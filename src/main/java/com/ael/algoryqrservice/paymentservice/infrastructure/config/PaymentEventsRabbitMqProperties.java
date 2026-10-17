package com.ael.algoryqrservice.paymentservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "payment.events")
public record PaymentEventsRabbitMqProperties(
        String exchange,
        List<String> allowedServiceNames
) {
    public PaymentEventsRabbitMqProperties {
        if (exchange == null || exchange.isBlank()) {
            exchange = "payment.events";
        }
        if (allowedServiceNames == null) {
            allowedServiceNames = new ArrayList<>();
        }
    }
}
