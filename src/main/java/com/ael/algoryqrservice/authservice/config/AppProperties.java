package com.ael.algoryqrservice.authservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String url = "http://localhost:3000";
    private String serviceName = "algoryqr-authservice";
    private CorsSettings cors = new CorsSettings();

    @Getter
    @Setter
    public static class CorsSettings {
        private String allowedOrigins = "http://localhost:3000,http://localhost:3001";
    }
}
