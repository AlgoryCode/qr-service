package com.ael.algoryqrservice.qrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.service")
public class PaymentClientProperties {

    private int pendingTimeoutMinutes = 30;
    private String gatewayProvider;
}
