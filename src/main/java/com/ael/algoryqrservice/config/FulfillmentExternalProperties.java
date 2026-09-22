package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "fulfillment.external")
public class FulfillmentExternalProperties {

    private boolean enabled = false;
    private String baseUrl = "http://localhost:8086";
    private String authToken = "";
    private String authHeader = "X-Service-Token";
}
