package com.ael.algoryqrservice.integration.ubereats.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ubereats")
public class UberEatsProperties {

    private String baseUrl = "https://api.tgoapis.com";
    private String userAgentName = "AlgoryQR";
    private String encryptKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(20);
    private int maxAttempts = 3;
    private boolean pollEnabled = true;
    private int pollLookbackHours = 168;
    private int pollPageSize = 50;
    private String webhookApiKey = "";
    private int defaultPreparationMinutes = 30;
    private int defaultCancelReasonId = 661;
    private final Paths paths = new Paths();

    @Getter
    @Setter
    public static class Paths {
        private String restaurants = "/integrator/store/meal/suppliers/{sellerId}/stores";
        private String restaurantMenu = "/integrator/product/meal/suppliers/{sellerId}/stores/{restaurantId}/products";
        private String orders = "/integrator/order/meal/suppliers/{sellerId}/packages";
        private String orderAccept = "/integrator/order/meal/suppliers/{sellerId}/packages/picked";
        private String orderReject = "/integrator/order/meal/suppliers/{sellerId}/packages/unsupplied";
        private String orderCancel = "/integrator/order/meal/suppliers/{sellerId}/packages/unsupplied";
        private String orderReady = "/integrator/order/meal/suppliers/{sellerId}/packages/invoiced";
    }
}
