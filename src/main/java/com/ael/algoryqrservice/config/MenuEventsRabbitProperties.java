package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "menu.rabbitmq")
public class MenuEventsRabbitProperties {

    private String exchange = "menu.events";
    private String productIndexRoutingKey = "menu.product.changed";
    private boolean publishEnabled = true;
}
