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

    private String url = "http://localhost:8080";
    private int pendingTimeoutMinutes = 30;
    private String authToken = "";
    private String authHeader = "X-Service-Token";
    private String gatewayProvider;

    public String getUrl() {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url;
    }
}
