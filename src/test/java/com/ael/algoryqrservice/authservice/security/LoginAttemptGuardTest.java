package com.ael.algoryqrservice.authservice.security;

import com.ael.algoryqrservice.authservice.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptGuardTest {

    private static final String ACCOUNT = "owner@example.com";

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptGuard guard = new LoginAttemptGuard(3, 15, clock);

    @Test
    void allowsAttemptsUpToTheLimit() {
        guard.registerFailure(ACCOUNT);
        guard.registerFailure(ACCOUNT);
        guard.registerFailure(ACCOUNT);

        assertThatCode(() -> guard.assertNotBlocked(ACCOUNT)).doesNotThrowAnyException();
    }

    @Test
    void locksAfterTheLimitAndDoublesTheWait() {
        for (int i = 0; i < 4; i++) guard.registerFailure(ACCOUNT); // 1 minute lock
        assertThatThrownBy(() -> guard.assertNotBlocked(ACCOUNT)).isInstanceOf(TooManyRequestsException.class);

        clock.advance(Duration.ofSeconds(61));
        assertThatCode(() -> guard.assertNotBlocked(ACCOUNT)).doesNotThrowAnyException();

        guard.registerFailure(ACCOUNT); // 2 minute lock
        clock.advance(Duration.ofSeconds(90));
        assertThatThrownBy(() -> guard.assertNotBlocked(ACCOUNT)).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofSeconds(31));
        assertThatCode(() -> guard.assertNotBlocked(ACCOUNT)).doesNotThrowAnyException();
    }

    @Test
    void capsTheLockAndResetsOnSuccess() {
        for (int i = 0; i < 30; i++) guard.registerFailure(ACCOUNT);
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThatCode(() -> guard.assertNotBlocked(ACCOUNT)).doesNotThrowAnyException();

        guard.reset(ACCOUNT);
        guard.registerFailure(ACCOUNT);
        assertThatCode(() -> guard.assertNotBlocked(ACCOUNT)).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-11T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
