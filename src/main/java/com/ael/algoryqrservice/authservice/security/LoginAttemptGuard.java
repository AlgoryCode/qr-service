package com.ael.algoryqrservice.authservice.security;

import com.ael.algoryqrservice.authservice.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Slows down password guessing per account with an escalating lock instead of a fixed one:
 * the first {@code maxFailures} misses are free, then each further miss doubles the wait
 * (1, 2, 4 … minutes, capped at {@code maxLockMinutes}). A real user who mistypes a few times is
 * barely affected, while a scripted attack quickly runs into long waits.
 *
 * <p>State is in-process: it resets on restart and is not shared between instances. Moving it to a
 * shared store (Redis) and adding a per-client-IP limit both need infrastructure that does not exist
 * yet — the IP limit additionally needs the BFF to forward a trusted client IP, otherwise every
 * dashboard login looks like it comes from the same proxy address.
 */
@Component
public class LoginAttemptGuard {

    private static final int MAX_TRACKED_ACCOUNTS = 100_000;

    private final int maxFailures;
    private final long maxLockMinutes;
    private final Clock clock;
    private final Cache<String, Attempts> attemptsByAccount;

    @Autowired
    public LoginAttemptGuard(
            @Value("${app.auth-gateway.login-max-failures:10}") int maxFailures,
            @Value("${app.auth-gateway.login-failure-window-minutes:15}") int windowMinutes
    ) {
        this(maxFailures, windowMinutes, Clock.systemUTC());
    }

    LoginAttemptGuard(int maxFailures, int windowMinutes, Clock clock) {
        this.maxFailures = maxFailures;
        this.maxLockMinutes = windowMinutes;
        this.clock = clock;
        // Forget an account once it has been quiet for twice the longest possible lock.
        this.attemptsByAccount = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(windowMinutes * 2L))
                .maximumSize(MAX_TRACKED_ACCOUNTS)
                .build();
    }

    public void assertNotBlocked(String account) {
        Attempts attempts = attemptsByAccount.getIfPresent(account);
        if (attempts == null || attempts.lockedUntil() == null) {
            return;
        }
        Instant now = clock.instant();
        if (attempts.lockedUntil().isAfter(now)) {
            long seconds = Math.max(1, Duration.between(now, attempts.lockedUntil()).toSeconds());
            throw new TooManyRequestsException(
                    "Çok fazla hatalı giriş denemesi yapıldı. Lütfen " + humanize(seconds) + " sonra tekrar deneyin."
            );
        }
    }

    public void registerFailure(String account) {
        attemptsByAccount.asMap().compute(account, (key, previous) -> {
            int failures = (previous == null ? 0 : previous.failures()) + 1;
            return new Attempts(failures, lockUntil(failures));
        });
    }

    public void reset(String account) {
        attemptsByAccount.invalidate(account);
    }

    private Instant lockUntil(int failures) {
        int over = failures - maxFailures;
        if (over <= 0) {
            return null;
        }
        long minutes = Math.min(maxLockMinutes, 1L << Math.min(over - 1, 20));
        return clock.instant().plus(Duration.ofMinutes(minutes));
    }

    private static String humanize(long seconds) {
        if (seconds < 60) {
            return seconds + " saniye";
        }
        return ((seconds + 59) / 60) + " dakika";
    }

    private record Attempts(int failures, Instant lockedUntil) {
    }
}
