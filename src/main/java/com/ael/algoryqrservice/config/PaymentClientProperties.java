package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.service")
public class PaymentClientProperties {

    private String url = "http://paymentservice:8080";
    private int pendingTimeoutMinutes = 30;
    private String authToken = "";
    private String authHeader = "X-Service-Token";
}
