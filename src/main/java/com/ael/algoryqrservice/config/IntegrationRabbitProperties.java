package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "integration.rabbitmq")
public class IntegrationRabbitProperties {
    private String exchange = "integration.events";
    private String aiRequestedQueue = "integration.ai.requested";
    private String aiRequestedRoutingKey = "integration.ai.requested";
    private String aiCompletedQueue = "qr-service.integration.ai.completed";
    private String aiCompletedRoutingKey = "integration.ai.completed";
    private String publishQueue = "qr-service.integration.publish.requested";
    private String publishRoutingKey = "integration.publish.requested";
}
