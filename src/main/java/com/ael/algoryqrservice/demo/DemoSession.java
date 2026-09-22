package com.ael.algoryqrservice.demo;

import com.ael.algoryqrservice.util.ClientInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

final class DemoSession {

    private final UUID id;
    private final Long userId;
    private final String email;
    private final LocalDateTime loggedInAt;
    private final LocalDateTime refreshExpiresAt;
    private final String ipAddress;
    private final String userAgent;
    private final String device;
    private final String deviceType;
    private String refreshToken;
    private LocalDateTime lastActivityAt;
    private LocalDateTime accessExpiresAt;
    private boolean revoked;
    private LocalDateTime revokedAt;

    DemoSession(
            UUID id,
            Long userId,
            String email,
            String refreshToken,
            LocalDateTime now,
            LocalDateTime accessExpiresAt,
            LocalDateTime refreshExpiresAt,
            ClientInfo clientInfo
    ) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.refreshToken = refreshToken;
        this.loggedInAt = now;
        this.lastActivityAt = now;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.ipAddress = clientInfo.ipAddress();
        this.userAgent = clientInfo.userAgent();
        this.device = clientInfo.device();
        this.deviceType = clientInfo.deviceType();
    }

    UUID id() {
        return id;
    }

    Long userId() {
        return userId;
    }

    String email() {
        return email;
    }

    String refreshToken() {
        return refreshToken;
    }

    LocalDateTime loggedInAt() {
        return loggedInAt;
    }

    LocalDateTime lastActivityAt() {
        return lastActivityAt;
    }

    LocalDateTime accessExpiresAt() {
        return accessExpiresAt;
    }

    LocalDateTime refreshExpiresAt() {
        return refreshExpiresAt;
    }

    boolean revoked() {
        return revoked;
    }

    LocalDateTime revokedAt() {
        return revokedAt;
    }

    String ipAddress() {
        return ipAddress;
    }

    String userAgent() {
        return userAgent;
    }

    String device() {
        return device;
    }

    String deviceType() {
        return deviceType;
    }

    boolean active(LocalDateTime now) {
        return !revoked && refreshExpiresAt.isAfter(now);
    }

    boolean matchesRefresh(String token) {
        if (token == null || refreshToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                refreshToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        );
    }

    void rotate(String nextRefreshToken, LocalDateTime now, LocalDateTime nextAccessExpiry) {
        refreshToken = nextRefreshToken;
        lastActivityAt = now;
        accessExpiresAt = nextAccessExpiry;
    }

    void revoke(LocalDateTime now) {
        revoked = true;
        revokedAt = now;
        lastActivityAt = now;
    }
}
