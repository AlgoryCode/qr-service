package com.ael.algoryqrservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "google.oauth")
public record GoogleOAuthProperties(
        @DefaultValue("http://localhost:3000/api/auth/google/callback")
        String frontendCallbackUrl,
        @DefaultValue("2m")
        Duration handoffTicketTtl
) {
}
