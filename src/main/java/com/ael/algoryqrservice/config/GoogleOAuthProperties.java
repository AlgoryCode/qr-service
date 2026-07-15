package com.ael.algoryqrservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "google.oauth")
public record GoogleOAuthProperties(
        String frontendCallbackUrl,
        Duration handoffTicketTtl
) {
}
