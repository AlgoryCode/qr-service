package com.ael.algoryqrservice.paymentservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "push-notification")
public record PushNotificationProperties(
        Messaging messaging,
        List<String> channels,
        String paymentSubject
) {

    public record Messaging(
            String exchange,
            String routingKey
    ) {
    }
}
