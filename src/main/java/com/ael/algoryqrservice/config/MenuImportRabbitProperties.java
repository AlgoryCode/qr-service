package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "menu-import.rabbitmq")
public class MenuImportRabbitProperties {
    private String exchange = "menu.import.events";
    private String aiRequestedQueue = "menu.import.ai.requested";
    private String aiRequestedRoutingKey = "menu.import.ai.requested";
    private String aiCompletedQueue = "qr-service.menu.import.ai.completed";
    private String aiCompletedRoutingKey = "menu.import.ai.completed";
}
