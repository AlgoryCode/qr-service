package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.JwtProperties;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String PRINCIPAL_TYPE_CLAIM = "principalType";
    public static final String PRINCIPAL_APP = "APP";
    public static final String PRINCIPAL_DASHBOARD = "DASHBOARD";
    public static final String PRINCIPAL_CUSTOMER = "CUSTOMER";
    public static final String PRINCIPAL_WAITER = "WAITER";
    public static final String SUBJECT_TYPE_CLAIM = "subjectType";
    public static final String SUBJECT_MERCHANT = "MERCHANT";
    public static final String SUBJECT_STAFF = "STAFF";
    public static final String SUBJECT_ADMIN = "ADMIN";

    private static final Set<String> AUTH_SERVICE_SUBJECTS = Set.of(
            SUBJECT_MERCHANT,
            SUBJECT_STAFF,
            SUBJECT_ADMIN
    );
    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String ROLES_CLAIM = "roles";
    private static final String PROVIDER_CLAIM = "provider";

    private final JwtProperties jwtProperties;

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

    public String extractPrincipalType(Claims claims) {
        String type = claims.get(PRINCIPAL_TYPE_CLAIM, String.class);
        return type == null || type.isBlank() ? PRINCIPAL_APP : type;
    }

    public String extractSubjectType(Claims claims) {
        return claims.get(SUBJECT_TYPE_CLAIM, String.class);
    }

    public boolean isAuthServiceSubject(Claims claims) {
        String subjectType = extractSubjectType(claims);
        return subjectType != null && AUTH_SERVICE_SUBJECTS.contains(subjectType);
    }

    public Long extractPackageOwnerId(Claims claims) {
        if (SUBJECT_STAFF.equals(extractSubjectType(claims))) {
            Long merchantId = extractMerchantId(claims);
            if (merchantId != null) {
                return merchantId;
            }
        }
        return extractUserId(claims);
    }

    public boolean isDashboardPrincipal(Claims claims) {
        return PRINCIPAL_DASHBOARD.equals(extractPrincipalType(claims));
    }

    public boolean isCustomerPrincipal(Claims claims) {
        return PRINCIPAL_CUSTOMER.equals(extractPrincipalType(claims));
    }

    public boolean isWaiterPrincipal(Claims claims) {
        return PRINCIPAL_WAITER.equals(extractPrincipalType(claims));
    }

    public Long extractBranchId(Claims claims) {
        return extractLongClaim(claims, "branchId");
    }

    public Long extractMerchantId(Claims claims) {
        Long merchantId = extractLongClaim(claims, "merchantId");
        if (merchantId != null) {
            return merchantId;
        }
        return extractLongClaim(claims, "ownerUserId");
    }

    private Long extractLongClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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

    public Long extractUserId(Claims claims) {
        Object value = claims.get("userId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractScopes(Claims claims) {
        Object scopes = claims.get("scopes");
        if (scopes instanceof List<?> scopeList) {
            return scopeList.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractProducts(Claims claims) {
        Object products = claims.get("products");
        if (products instanceof List<?> productList) {
            return productList.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    public String extractActivePackage(Claims claims) {
        Object value = claims.get("activePackage");
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get(ROLES_CLAIM);
        if (roles instanceof List<?> roleList) {
            return roleList.stream().map(Object::toString).toList();
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
