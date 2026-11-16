package com.ael.algoryqrservice.qrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ai-service")
public class AiServiceProperties {

    private String url = "http://localhost:8000";
    /** Sent as {@code X-API-Key}; ai-service rejects batch, import and reindex calls without it. */
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(30);
    private int reindexBatchSize = 100;
}
