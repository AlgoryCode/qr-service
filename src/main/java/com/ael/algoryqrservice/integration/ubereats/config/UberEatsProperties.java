package com.ael.algoryqrservice.integration.ubereats.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ubereats-menu")
public class UberEatsProperties {

    private String apiBaseUrl = "https://api.uber.com";
    private String authUrl = "https://login.uber.com/oauth/v2/token";
    private String encryptKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);
    private int maxAttempts = 3;
    private String defaultScope = "eats.store";
}
