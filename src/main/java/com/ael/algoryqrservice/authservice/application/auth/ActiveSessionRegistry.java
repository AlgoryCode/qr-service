package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.SessionJpaRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Answers "is this access token's session still open?" so sign-out, "sign out other devices" and
 * password changes take effect on the next request instead of when the 15-minute access token expires.
 *
 * <p>Answers are cached for a few seconds to keep this off the database on every request. Sessions ended
 * in this process are marked closed immediately. If the database cannot be reached the token is trusted
 * (it is still short-lived and signed): a DB hiccup must not sign every user out.
 */
@Slf4j
@Component
public class ActiveSessionRegistry {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final SessionJpaRepository sessionRepository;
    private final Cache<UUID, Boolean> activeBySession = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .maximumSize(200_000)
            .build();

    public ActiveSessionRegistry(SessionJpaRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public boolean isActive(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        Boolean cached = activeBySession.getIfPresent(sessionId);
        if (cached != null) {
            return cached;
        }
        try {
            boolean active = sessionRepository.findByIdAndRevokedFalse(sessionId)
                    .map(session -> session.getRefreshExpiresAt() == null
                            || session.getRefreshExpiresAt().isAfter(LocalDateTime.now()))
                    .orElse(false);
            activeBySession.put(sessionId, active);
            return active;
        } catch (RuntimeException exception) {
            log.warn("Session check unavailable, trusting token sessionId={}: {}", sessionId, exception.getMessage());
            return true;
        }
    }

    /** Call when a session is revoked or expired so this instance stops accepting its access tokens at once. */
    public void markEnded(UUID sessionId) {
        if (sessionId != null) {
            activeBySession.put(sessionId, false);
        }
    }
}
