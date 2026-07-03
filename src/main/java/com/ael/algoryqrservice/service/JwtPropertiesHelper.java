package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class JwtPropertiesHelper {

    private final JwtProperties jwtProperties;

    public Duration getAccessDuration() {
        return Duration.ofMillis(jwtProperties.getAccessExpirationMs());
    }

    public Duration getRefreshDuration() {
        return Duration.ofMillis(jwtProperties.getRefreshExpirationMs());
    }
}
