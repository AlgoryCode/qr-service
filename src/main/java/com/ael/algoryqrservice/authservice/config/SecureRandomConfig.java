package com.ael.algoryqrservice.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration
public class SecureRandomConfig {

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
