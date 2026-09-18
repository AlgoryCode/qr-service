package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginAttemptGuard {

    private static final int MAX_TRACKED_ACCOUNTS = 100_000;

    private final int maxFailures;
    private final Cache<String, AtomicInteger> failuresByEmail;

    public LoginAttemptGuard(
            @Value("${app.auth-gateway.login-max-failures:10}") int maxFailures,
            @Value("${app.auth-gateway.login-failure-window-minutes:15}") int windowMinutes
    ) {
        this.maxFailures = maxFailures;
        this.failuresByEmail = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(windowMinutes))
                .maximumSize(MAX_TRACKED_ACCOUNTS)
                .build();
    }

    public void assertNotBlocked(String email) {
        AtomicInteger failures = failuresByEmail.getIfPresent(email);
        if (failures != null && failures.get() >= maxFailures) {
            throw new TooManyRequestsException(
                    "Çok fazla hatalı giriş denemesi yapıldı. Lütfen bir süre sonra tekrar deneyin."
            );
        }
    }

    public void registerFailure(String email) {
        failuresByEmail.get(email, key -> new AtomicInteger()).incrementAndGet();
    }

    public void reset(String email) {
        failuresByEmail.invalidate(email);
    }
}
