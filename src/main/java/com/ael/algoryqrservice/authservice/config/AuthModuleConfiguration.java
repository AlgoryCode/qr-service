package com.ael.algoryqrservice.authservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AppProperties.class,
        GoogleOAuthProperties.class,
        JwtProperties.class
})
public class AuthModuleConfiguration {
}
