package com.ael.algoryqrservice.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "google.oauth")
public record GoogleOAuthProperties(
        String frontendCallbackUrl,
        Duration handoffTicketTtl,
        String androidClientId,
        String postLoginRedirectPath
) {
    public String postLoginRedirectPath() {
        if (postLoginRedirectPath == null || postLoginRedirectPath.isBlank()) {
            return "/login";
        }
        return postLoginRedirectPath;
    }
}
