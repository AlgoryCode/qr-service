package com.ael.algoryqrservice.demo;

import com.ael.algoryqrservice.config.JwtProperties;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.SessionPageResponse;
import com.ael.algoryqrservice.model.dto.SessionResponse;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.service.JwtService;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class DemoSessionStore {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final ConcurrentMap<UUID, DemoSession> sessions = new ConcurrentHashMap<>();

    public AuthResponse open(User user, ClientInfo clientInfo) {
        LocalDateTime now = LocalDateTime.now();
        UUID sessionId = UUID.randomUUID();
        DemoSession session = new DemoSession(
                sessionId,
                user.getId(),
                user.getEmail(),
                newRefreshSecret(sessionId),
                now,
                now.plus(Duration.ofMillis(jwtProperties.getAccessExpirationMs())),
                now.plus(Duration.ofMillis(jwtProperties.getRefreshExpirationMs())),
                clientInfo
        );
        sessions.put(session.id(), session);
        return tokens(session);
    }

    public Optional<AuthResponse> refresh(String refreshToken) {
        DemoSession session = findByRefresh(refreshToken).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        synchronized (session) {
            return Optional.of(rotate(session, refreshToken));
        }
    }

    public boolean revokeAccess(UUID sessionId) {
        DemoSession session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        revoke(session);
        return true;
    }

    public boolean revokeRefresh(String refreshToken) {
        DemoSession session = findByRefresh(refreshToken).orElse(null);
        if (session == null) {
            return false;
        }
        revoke(session);
        return true;
    }

    public void revoke(UUID sessionId) {
        DemoSession session = sessions.get(sessionId);
        if (session == null) {
            throw new NotFoundException("Oturum bulunamadı");
        }
        revoke(session);
    }

    public boolean isActive(UUID sessionId) {
        DemoSession session = sessions.get(sessionId);
        return session != null && session.active(LocalDateTime.now());
    }

    public SessionPageResponse page(UUID currentSessionId) {
        List<SessionResponse> content = sessions.values().stream()
                .map(session -> toResponse(session, currentSessionId))
                .toList();
        return SessionPageResponse.builder()
                .content(content)
                .page(0)
                .size(Math.max(content.size(), 1))
                .totalElements(content.size())
                .totalPages(content.isEmpty() ? 0 : 1)
                .hasNext(false)
                .build();
    }

    private AuthResponse rotate(DemoSession session, String refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        if (!session.active(now) || !session.matchesRefresh(refreshToken)) {
            throw new UnauthorizedException("Geçersiz refresh token");
        }
        session.rotate(
                newRefreshSecret(session.id()),
                now,
                now.plus(Duration.ofMillis(jwtProperties.getAccessExpirationMs()))
        );
        return tokens(session);
    }

    private void revoke(DemoSession session) {
        synchronized (session) {
            if (!session.revoked()) {
                session.revoke(LocalDateTime.now());
            }
        }
    }

    private Optional<DemoSession> findByRefresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return sessions.values().stream()
                .filter(session -> session.matchesRefresh(refreshToken))
                .findFirst();
    }

    private AuthResponse tokens(DemoSession session) {
        String accessToken = jwtService.generateDemoAccessToken(
                session.email(),
                session.id(),
                session.userId(),
                DemoAccess.profile()
        );
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(session.refreshToken())
                .build();
    }

    private static String newRefreshSecret(UUID sessionId) {
        return sessionId + "." + UUID.randomUUID();
    }

    private static SessionResponse toResponse(DemoSession session, UUID currentSessionId) {
        LocalDateTime now = LocalDateTime.now();
        boolean active = session.active(now);
        return SessionResponse.builder()
                .sessionId(session.id())
                .loggedInAt(session.loggedInAt())
                .lastActivityAt(session.lastActivityAt())
                .accessExpiresAt(session.accessExpiresAt())
                .refreshExpiresAt(session.refreshExpiresAt())
                .revoked(session.revoked())
                .revokedAt(session.revokedAt())
                .expired(!active && !session.revoked())
                .active(active)
                .current(session.id().equals(currentSessionId))
                .ipAddress(session.ipAddress())
                .userAgent(session.userAgent())
                .device(session.device())
                .deviceType(session.deviceType())
                .provider(AuthProvider.BASIC)
                .build();
    }
}
