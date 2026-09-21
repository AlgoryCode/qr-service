package com.ael.algoryqrservice.integration.yemeksepeti.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "yemeksepeti")
public class YemekSepetiProperties {

    private String baseUrl = "https://yemeksepeti.partner.deliveryhero.io";
    private String encryptKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(20);
    private int maxAttempts = 3;
    private boolean pollEnabled = true;
    private int pollLookbackHours = 168;
    private String tokenPath = "/v2/oauth/token";
    private String vendorOrdersPath = "/v2/chains/{chainId}/vendors/{vendorId}/orders";
    private String orderPath = "/v2/chains/{chainId}/orders/{orderId}";
}
