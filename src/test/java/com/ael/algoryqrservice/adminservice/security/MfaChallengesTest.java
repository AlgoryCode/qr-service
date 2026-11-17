package com.ael.algoryqrservice.adminservice.security;

import com.ael.algoryqrservice.qrservice.exception.TooManyRequestsException;
import com.ael.algoryqrservice.qrservice.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaChallengesTest {

    private static final byte[] KEY = "test-key".getBytes(StandardCharsets.UTF_8);

    @Test
    void ticketRoundTripsUntilItExpires() {
        Instant t0 = Instant.parse("2026-11-17T10:00:00Z");
        String ticket = new MfaChallenges(KEY, Clock.fixed(t0, ZoneOffset.UTC)).issue(7L);
        assertThat(new MfaChallenges(KEY, Clock.fixed(t0.plusSeconds(60), ZoneOffset.UTC)).verify(ticket)).isEqualTo(7L);
        assertThatThrownBy(() -> new MfaChallenges(KEY, Clock.fixed(t0.plusSeconds(600), ZoneOffset.UTC)).verify(ticket))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsTamperedOrForeignTickets() {
        MfaChallenges challenges = new MfaChallenges(KEY, Clock.systemUTC());
        String ticket = challenges.issue(7L);
        String forged = "8" + ticket.substring(1);
        assertThatThrownBy(() -> challenges.verify(forged)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> new MfaChallenges("other".getBytes(StandardCharsets.UTF_8), Clock.systemUTC()).verify(ticket))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> challenges.verify("garbage")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void locksAfterRepeatedFailures() {
        MfaChallenges challenges = new MfaChallenges(KEY, Clock.systemUTC());
        for (int i = 0; i < 5; i++) {
            challenges.checkNotLocked(3L);
            challenges.recordFailure(3L);
        }
        assertThatThrownBy(() -> challenges.checkNotLocked(3L)).isInstanceOf(TooManyRequestsException.class);
        challenges.clearFailures(3L);
        challenges.checkNotLocked(3L);
    }
}
