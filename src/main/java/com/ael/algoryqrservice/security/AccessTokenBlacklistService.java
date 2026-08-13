package com.ael.algoryqrservice.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class AccessTokenBlacklistService {

    private final Cache<UUID, Boolean> blacklist = Caffeine.newBuilder()
            .expireAfter(new Expiry<UUID, Boolean>() {
                @Override
                public long expireAfterCreate(UUID key, Boolean value, long currentTime) {
                    // Duration is set per entry via expireVariably().put(...)
                    return Long.MAX_VALUE;
                }

                @Override
                public long expireAfterUpdate(UUID key, Boolean value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(UUID key, Boolean value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    /**
     * Blacklists the access token identified by sessionId (JWT jti) until accessExpiresAt.
     * No-op if accessExpiresAt is null or already in the past.
     */
    public void blacklist(UUID sessionId, LocalDateTime accessExpiresAt) {
        if (sessionId == null || accessExpiresAt == null) {
            return;
        }

        Duration remaining = Duration.between(LocalDateTime.now(), accessExpiresAt);
        if (remaining.isZero() || remaining.isNegative()) {
            return;
        }

        blacklist.policy().expireVariably()
                .orElseThrow(() -> new IllegalStateException("Variable expiration is not enabled"))
                .put(sessionId, Boolean.TRUE, remaining);
    }

    public boolean isBlacklisted(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        return Boolean.TRUE.equals(blacklist.getIfPresent(sessionId));
    }
}
