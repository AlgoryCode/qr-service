package com.ael.algoryqrservice.authservice.config;

import com.ael.algoryqrservice.authservice.application.auth.GoogleIdTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleIdTokenVerifierConfig {

    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String webClientId,
            GoogleOAuthProperties properties
    ) {
        String androidClientId = properties.androidClientId() == null ? "" : properties.androidClientId();
        return GoogleIdTokenVerifier.fromClientIds(webClientId, androidClientId);
    }
}
