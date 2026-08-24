package com.ael.algoryqrservice.integration.trendyolgo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "trendyol-go")
public class TrendyolGoProperties {

    private String baseUrl = "https://api.tgoapps.com";
    private String userAgentName = "AlgoryQR";
    private String encryptKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(20);
    private int maxAttempts = 3;
    private boolean pollEnabled = true;
    private int pollLookbackHours = 24;
    private String webhookApiKey = "";
    private final Paths paths = new Paths();

    @Getter
    @Setter
    public static class Paths {
        private String restaurants = "/meal/sellers/{sellerId}/restaurants";
        private String restaurantMenu = "/meal/sellers/{sellerId}/restaurants/{restaurantId}/menu";
        private String orders = "/meal/sellers/{sellerId}/orders";
        private String orderAccept = "/meal/sellers/{sellerId}/orders/{orderId}/accepted";
        private String orderReject = "/meal/sellers/{sellerId}/orders/{orderId}/rejected";
        private String orderCancel = "/meal/sellers/{sellerId}/orders/{orderId}/cancelled";
        private String orderReady = "/meal/sellers/{sellerId}/orders/{orderId}/prepared";
    }
}
