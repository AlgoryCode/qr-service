package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.JwtProperties;
import com.ael.algoryqrservice.model.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String ROLES_CLAIM = "roles";

    private final JwtProperties jwtProperties;

    public String generateAccessToken(String email, UUID sessionId, Long userId, UserRole role) {
        Date now = new Date();
        return Jwts.builder()
                .id(sessionId.toString())
                .subject(email)
                .claim("userId", userId)
                .claim(ROLES_CLAIM, resolveRoles(role))
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtProperties.getAccessExpirationMs()))
                .signWith(getSigningKey())
                .compact();
    }

    public Optional<Claims> parseValidAccessToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                return Optional.empty();
            }
            if (claims.getExpiration().before(new Date())) {
                return Optional.empty();
            }
            if (claims.getId() == null || claims.getId().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public UUID extractSessionId(Claims claims) {
        return UUID.fromString(claims.getId());
    }

    public Optional<UUID> extractSessionIdIfSignatureValid(String token) {
        try {
            return Optional.of(extractSessionIdFromClaims(extractAllClaims(token)));
        } catch (ExpiredJwtException e) {
            return Optional.ofNullable(e.getClaims()).flatMap(this::extractSessionIdFromClaimsOptional);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<UUID> extractSessionIdFromClaimsOptional(Claims claims) {
        try {
            return Optional.of(extractSessionIdFromClaims(claims));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private UUID extractSessionIdFromClaims(Claims claims) {
        if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("Invalid token type");
        }
        if (claims.getId() == null || claims.getId().isBlank()) {
            throw new IllegalArgumentException("Missing session id");
        }
        return UUID.fromString(claims.getId());
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get(ROLES_CLAIM);
        if (roles instanceof List<?> roleList) {
            return roleList.stream().map(Object::toString).toList();
        }
        return List.of("ROLE_USER");
    }

    private List<String> resolveRoles(UserRole role) {
        if (role == UserRole.ADMIN) {
            return List.of("ROLE_USER", "ROLE_ADMIN");
        }
        return List.of("ROLE_USER");
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
