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
    private String successQueue = "qr-service.payment.success";
    private String failedQueue = "qr-service.payment.failed";
    private String successRoutingKey = "qr-service.payment.success";
    private String failedRoutingKey = "qr-service.payment.failed";
}
