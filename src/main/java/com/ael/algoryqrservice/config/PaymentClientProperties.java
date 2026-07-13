package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payment.service")
public class PaymentClientProperties {

    private String url = "http://payment-service:8080";
    private int pendingTimeoutMinutes = 30;
}
