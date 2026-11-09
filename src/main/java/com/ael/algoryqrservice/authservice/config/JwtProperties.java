package com.ael.algoryqrservice.authservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret;
    private long accessExpirationMs = 900_000;
    private long refreshExpirationMs = 604_800_000;
    /** Absolute session lifetime: refreshes slide the expiry, but a sign-in is needed again after this (30 days). */
    private long refreshMaxLifetimeMs = 2_592_000_000L;
}
