package com.ael.algoryqrservice.adminservice.security;

import com.ael.algoryqrservice.qrservice.config.JwtProperties;
import com.ael.algoryqrservice.qrservice.exception.TooManyRequestsException;
import com.ael.algoryqrservice.qrservice.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The step between a correct password and the second factor: a short-lived signed ticket naming the
 * account, plus a cap on wrong codes per account so the 6-digit code cannot be guessed.
 */
@Component
public class MfaChallenges {

    static final Duration TICKET_TTL = Duration.ofMinutes(5);
    static final int MAX_FAILURES = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private final byte[] key;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public MfaChallenges(JwtProperties jwtProperties) {
        this(("mfa:" + jwtProperties.getSecret()).getBytes(StandardCharsets.UTF_8), Clock.systemUTC());
    }

    MfaChallenges(byte[] key, Clock clock) {
        this.key = key;
        this.clock = clock;
    }

    public String issue(Long dashboardUserId) {
        long expires = clock.instant().plus(TICKET_TTL).getEpochSecond();
        String payload = dashboardUserId + "." + expires + "." + UUID.randomUUID();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    /** The account the ticket was issued for; rejects tampered or expired tickets. */
    public Long verify(String ticket) {
        if (ticket == null || !ticket.contains(".")) {
            throw new UnauthorizedException("Doğrulama süresi doldu, tekrar giriş yapın");
        }
        String encoded = ticket.substring(0, ticket.lastIndexOf('.'));
        String signature = ticket.substring(ticket.lastIndexOf('.') + 1);
        if (!MessageDigest.isEqual(sign(encoded).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new UnauthorizedException("Doğrulama süresi doldu, tekrar giriş yapın");
        }
        String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\.");
        if (parts.length < 2 || Long.parseLong(parts[1]) < clock.instant().getEpochSecond()) {
            throw new UnauthorizedException("Doğrulama süresi doldu, tekrar giriş yapın");
        }
        return Long.parseLong(parts[0]);
    }

    public void checkNotLocked(Long dashboardUserId) {
        if (recentFailures(dashboardUserId).size() >= MAX_FAILURES) {
            throw new TooManyRequestsException("Çok fazla hatalı kod girildi. 15 dakika sonra tekrar deneyin.");
        }
    }

    public void recordFailure(Long dashboardUserId) {
        recentFailures(dashboardUserId).addLast(clock.instant());
    }

    public void clearFailures(Long dashboardUserId) {
        failures.remove(dashboardUserId);
    }

    private Deque<Instant> recentFailures(Long dashboardUserId) {
        Deque<Instant> deque = failures.computeIfAbsent(dashboardUserId, id -> new ArrayDeque<>());
        Instant cutoff = clock.instant().minus(FAILURE_WINDOW);
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
        }
        return deque;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("MFA signing unavailable", e);
        }
    }
}
