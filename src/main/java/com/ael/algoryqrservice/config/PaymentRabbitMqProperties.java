package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payment.rabbitmq")
public class PaymentRabbitMqProperties {

    private String exchange = "payment.events";
    private String serviceName = "qr-service";
    private String eventsQueue = "qr-service.payment.events";
    private String eventsRoutingKey = "qr-service.payment.events";
}
