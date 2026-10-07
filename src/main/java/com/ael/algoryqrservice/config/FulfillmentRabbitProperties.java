package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "fulfillment.rabbitmq")
public class FulfillmentRabbitProperties {

    private String exchange = "payment.events";
    private String routingKey = "qr-fulfillment-service.payment.events";
}
