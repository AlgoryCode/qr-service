package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.config.JwtProperties;
import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.SessionJpaRepository;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionIssuer {

    private final SessionJpaRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;

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

        List<String> scopes = scopesOf(subject);
        String accessToken = buildAccessToken(subject, sessionId, scopes);
        String refreshToken = formatRefreshToken(sessionId, rawRefresh, session.getRefreshExpiresAt());
        return new IssuedTokens(
                accessToken,
                refreshToken,
                sessionId,
                scopes,
                isoOffset(session.getAccessExpiresAt()),
                isoOffset(session.getRefreshExpiresAt())
        );
    }

    @AuthTransactional
    public IssuedTokens rotate(SessionEntity session, AuthSubject subject, ClientInfo clientInfo) {
        String rawRefresh = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        session.setRefreshTokenHash(passwordEncoder.encode(rawRefresh));
        session.setAccessExpiresAt(now.plusSeconds(jwtProperties.getAccessExpirationMs() / 1000));
        session.setRefreshExpiresAt(now.plusSeconds(jwtProperties.getRefreshExpirationMs() / 1000));
        session.setLastActivityAt(now);
        session.setIpAddress(clientInfo.ipAddress());
        session.setUserAgent(clientInfo.userAgent());
        session.setDevice(clientInfo.device());
        session.setDeviceType(clientInfo.deviceType());
        sessionRepository.save(session);

        List<String> scopes = scopesOf(subject);
        String accessToken = buildAccessToken(subject, session.getId(), scopes);
        String refreshToken = formatRefreshToken(session.getId(), rawRefresh, session.getRefreshExpiresAt());
        return new IssuedTokens(
                accessToken,
                refreshToken,
                session.getId(),
                scopes,
                isoOffset(session.getAccessExpiresAt()),
                isoOffset(session.getRefreshExpiresAt())
        );
    }

    @AuthTransactional
    public void revoke(SessionEntity session) {
        session.setRevoked(true);
        session.setLastActivityAt(LocalDateTime.now());
        sessionRepository.save(session);
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

    public String buildAccessToken(AuthSubject subject, UUID sessionId, List<String> scopes) {
        Date now = new Date();
        var builder = Jwts.builder()
                .id(sessionId.toString())
                .subject(subject.loginIdentifier())
                .claim("subjectType", subject.subjectType())
                .claim("roles", List.of("ROLE_" + subject.roleCode()))
                .claim("scopes", scopes)
                .claim("provider", subject.provider())
                .claim("typ", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtProperties.getAccessExpirationMs()))
                .signWith(signingKey());
        if (AuthSubject.TYPE_MERCHANT.equals(subject.subjectType())) {
            Long merchantId = subject.merchantId() != null ? subject.merchantId() : subject.subjectId();
            builder.claim("merchantId", merchantId);
        } else {
            builder.claim("userId", subject.subjectId());
            if (subject.merchantId() != null) {
                builder.claim("merchantId", subject.merchantId());
            }
        }
        if (subject.branchId() != null) {
            builder.claim("branchId", subject.branchId());
        }
        return builder.compact();
    }

    private static List<String> scopesOf(AuthSubject subject) {
        return subject.scopes() == null ? List.of() : List.copyOf(subject.scopes());
    }

    private String formatRefreshToken(UUID sessionId, String rawRefresh, LocalDateTime expiresAt) {
        Date expires = Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant());
        return Jwts.builder()
                .id(sessionId.toString())
                .claim("raw", rawRefresh)
                .claim("typ", "refresh")
                .issuedAt(new Date())
                .expiration(expires)
                .signWith(signingKey())
                .compact();
    }

    private RefreshTokenParts parseJwtRefreshToken(String refreshToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();
            if (!"refresh".equals(claims.get("typ", String.class)) || claims.getId() == null) {
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

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
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
}
