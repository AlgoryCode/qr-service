package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.config.JwtProperties;
import com.ael.algoryqrservice.authservice.security.JwtTokenCodec;
import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.SessionJpaRepository;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionIssuer {

    /** How long a rotated-out refresh token is still accepted: enough for a client to retry a lost response. */
    static final Duration REFRESH_GRACE = Duration.ofSeconds(60);

    private final SessionJpaRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final LoginPackageClaims loginPackageClaims;
    private final JwtTokenCodec tokenCodec;
    private final ActiveSessionRegistry activeSessions;

    @AuthTransactional
    public IssuedTokens issue(AuthSubject subject, ClientInfo clientInfo) {
        UUID sessionId = UUID.randomUUID();
        String rawRefresh = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        SessionEntity session = new SessionEntity();
        session.setId(sessionId);
        session.setSubjectType(subject.subjectType());
        session.setSubjectId(subject.subjectId());
        session.setRoleCode(subject.roleCode());
        session.setRefreshTokenHash(passwordEncoder.encode(rawRefresh));
        session.setLoggedInAt(now);
        session.setAccessExpiresAt(now.plusSeconds(jwtProperties.getAccessExpirationMs() / 1000));
        session.setRefreshExpiresAt(now.plusSeconds(jwtProperties.getRefreshExpirationMs() / 1000));
        session.setLastActivityAt(now);
        session.setRevoked(false);
        session.setIpAddress(clientInfo.ipAddress());
        session.setUserAgent(clientInfo.userAgent());
        session.setDevice(clientInfo.device());
        session.setDeviceType(clientInfo.deviceType());
        sessionRepository.save(session);

        TokenAccess access = tokenAccess(subject);
        String accessToken = buildAccessToken(subject, sessionId, access);
        String refreshToken = formatRefreshToken(sessionId, rawRefresh, session.getRefreshExpiresAt());
        return new IssuedTokens(
                accessToken,
                refreshToken,
                sessionId,
                access.scopes(),
                isoOffset(session.getAccessExpiresAt()),
                isoOffset(session.getRefreshExpiresAt())
        );
    }

    @AuthTransactional
    public IssuedTokens rotate(SessionEntity session, AuthSubject subject, ClientInfo clientInfo) {
        return rotate(session, subject, clientInfo, false);
    }

    /**
     * Issues a new pair for the session. Normally the replaced token becomes the one honoured for {@link #REFRESH_GRACE};
     * a retry inside that window ([retryOfPrevious]) keeps the original previous token and window, so a client
     * retrying a lost response neither extends the window nor demotes the token it is about to receive.
     */
    @AuthTransactional
    public IssuedTokens rotate(SessionEntity session, AuthSubject subject, ClientInfo clientInfo, boolean retryOfPrevious) {
        String rawRefresh = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        // Built first: a subject that can no longer get a token must not leave a rotated-but-undelivered session.
        TokenAccess access = tokenAccess(subject);
        String accessToken = buildAccessToken(subject, session.getId(), access);

        if (!retryOfPrevious) {
            session.setPreviousRefreshTokenHash(session.getRefreshTokenHash());
            session.setPreviousRefreshValidUntil(now.plus(REFRESH_GRACE));
        }
        session.setRefreshTokenHash(passwordEncoder.encode(rawRefresh));
        session.setAccessExpiresAt(now.plusSeconds(jwtProperties.getAccessExpirationMs() / 1000));
        session.setRefreshExpiresAt(slidingRefreshExpiry(session, now));
        session.setLastActivityAt(now);
        session.setIpAddress(clientInfo.ipAddress());
        session.setUserAgent(clientInfo.userAgent());
        session.setDevice(clientInfo.device());
        session.setDeviceType(clientInfo.deviceType());
        sessionRepository.save(session);

        String refreshToken = formatRefreshToken(session.getId(), rawRefresh, session.getRefreshExpiresAt());
        return new IssuedTokens(
                accessToken,
                refreshToken,
                session.getId(),
                access.scopes(),
                isoOffset(session.getAccessExpiresAt()),
                isoOffset(session.getRefreshExpiresAt())
        );
    }

    /** Each refresh extends the session, but never past {@code refresh-max-lifetime-ms} after sign-in. */
    private LocalDateTime slidingRefreshExpiry(SessionEntity session, LocalDateTime now) {
        LocalDateTime sliding = now.plusSeconds(jwtProperties.getRefreshExpirationMs() / 1000);
        LocalDateTime signedIn = session.getLoggedInAt() != null ? session.getLoggedInAt() : now;
        LocalDateTime cap = signedIn.plusSeconds(jwtProperties.getRefreshMaxLifetimeMs() / 1000);
        return sliding.isAfter(cap) ? cap : sliding;
    }

    /** Revokes in its own transaction, so it sticks even when the caller then answers with an error. */
    @AuthTransactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeNow(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(this::revoke);
    }

    @AuthTransactional
    public void revoke(SessionEntity session) {
        session.setRevoked(true);
        session.setLastActivityAt(LocalDateTime.now());
        sessionRepository.save(session);
        activeSessions.markEnded(session.getId());
    }

    public RefreshTokenParts parseRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        if (refreshToken.chars().filter(ch -> ch == '.').count() == 2) {
            return parseJwtRefreshToken(refreshToken);
        }
        int separator = refreshToken.indexOf('.');
        if (separator <= 0 || separator == refreshToken.length() - 1) {
            throw new UnauthorizedException("Invalid refresh token format");
        }
        try {
            UUID sessionId = UUID.fromString(refreshToken.substring(0, separator));
            String raw = refreshToken.substring(separator + 1);
            return new RefreshTokenParts(sessionId, raw);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid refresh token format");
        }
    }

    public boolean matchesRefresh(SessionEntity session, String rawRefresh) {
        return passwordEncoder.matches(rawRefresh, session.getRefreshTokenHash());
    }

    /** The token the last rotation replaced, whether or not its grace window is still open. */
    public boolean matchesPreviousRefresh(SessionEntity session, String rawRefresh) {
        return session.getPreviousRefreshTokenHash() != null
                && passwordEncoder.matches(rawRefresh, session.getPreviousRefreshTokenHash());
    }

    public String buildAccessToken(AuthSubject subject, UUID sessionId, List<String> scopes) {
        return buildAccessToken(subject, sessionId, new TokenAccess(mergeScopes(scopes, List.of()), LoginPackageClaims.Snapshot.none()));
    }

    private String buildAccessToken(AuthSubject subject, UUID sessionId, TokenAccess access) {
        Date now = new Date();
        var builder = tokenCodec.builder(JwtTokenCodec.TYPE_ACCESS)
                .id(sessionId.toString())
                .subject(subject.loginIdentifier())
                .claim("subjectType", subject.subjectType())
                .claim("roles", List.of("ROLE_" + subject.roleCode()))
                .claim("scopes", access.scopes())
                .claim("provider", subject.provider())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtProperties.getAccessExpirationMs()));
        if (access.snapshot().present()) {
            builder.claim("activePackage", access.snapshot().activePackage());
            builder.claim("products", access.snapshot().products());
        }
        if (AuthSubject.TYPE_MERCHANT.equals(subject.subjectType())) {
            Long merchantId = subject.merchantId() != null ? subject.merchantId() : subject.subjectId();
            builder.claim("merchantId", merchantId);
        } else if (AuthSubject.TYPE_STAFF.equals(subject.subjectType())) {
            if (subject.branchId() == null) {
                throw new UnauthorizedException("Personel bir şubeye atanmamış");
            }
            builder.claim("staffId", subject.subjectId());
            if (subject.merchantId() != null) {
                builder.claim("merchantId", subject.merchantId());
            }
        } else if (AuthSubject.TYPE_CUSTOMER.equals(subject.subjectType())) {
            builder.claim("customerId", subject.subjectId());
        } else if (AuthSubject.TYPE_ADMIN.equals(subject.subjectType())) {
            builder.claim("authSubjectId", subject.subjectId());
        }
        if (subject.branchId() != null) {
            builder.claim("branchId", subject.branchId());
        }
        return builder.compact();
    }

    private TokenAccess tokenAccess(AuthSubject subject) {
        LoginPackageClaims.Snapshot snapshot = loginPackageClaims.resolve(subject);
        return new TokenAccess(mergeScopes(scopesOf(subject), snapshot.productScopes()), snapshot);
    }

    private static List<String> scopesOf(AuthSubject subject) {
        return subject.scopes() == null ? List.of() : List.copyOf(subject.scopes());
    }

    private static List<String> mergeScopes(List<String> roleScopes, List<String> productScopes) {
        Set<String> merged = new LinkedHashSet<>();
        addScopes(merged, roleScopes);
        addScopes(merged, productScopes);
        return List.copyOf(merged);
    }

    private static void addScopes(Set<String> merged, List<String> scopes) {
        if (scopes == null) {
            return;
        }
        for (String scope : scopes) {
            if (scope != null && !scope.isBlank()) {
                merged.add(scope.trim());
            }
        }
    }

    private String formatRefreshToken(UUID sessionId, String rawRefresh, LocalDateTime expiresAt) {
        Date expires = Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant());
        return tokenCodec.builder(JwtTokenCodec.TYPE_REFRESH)
                .id(sessionId.toString())
                .claim("raw", rawRefresh)
                .issuedAt(new Date())
                .expiration(expires)
                .compact();
    }

    private RefreshTokenParts parseJwtRefreshToken(String refreshToken) {
        try {
            Claims claims = tokenCodec.parse(refreshToken, JwtTokenCodec.TYPE_REFRESH);
            if (claims.getId() == null) {
                throw new UnauthorizedException("Invalid refresh token");
            }
            String raw = claims.get("raw", String.class);
            if (raw == null || raw.isBlank()) {
                throw new UnauthorizedException("Invalid refresh token");
            }
            return new RefreshTokenParts(UUID.fromString(claims.getId()), raw);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid refresh token");
        }
    }

    private static String isoOffset(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
    }

    public record IssuedTokens(
            String accessToken,
            String refreshToken,
            UUID sessionId,
            List<String> scopes,
            String accessExpiresAt,
            String refreshExpiresAt
    ) {
    }

    public record RefreshTokenParts(UUID sessionId, String rawToken) {
    }

    private record TokenAccess(List<String> scopes, LoginPackageClaims.Snapshot snapshot) {
    }
}
