package com.ael.algoryqrservice.authservice.security;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAccessTokenParser {

    private final JwtProperties jwtProperties;

    public Optional<AuthPrincipal> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"access".equals(claims.get("typ", String.class))) {
                return Optional.empty();
            }
            String subjectType = claims.get("subjectType", String.class);
            Number merchantIdNum = claims.get("merchantId", Number.class);
            Number userIdNum = claims.get("userId", Number.class);
            Long userId = resolveUserId(subjectType, userIdNum, merchantIdNum);
            if (userId == null) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            @SuppressWarnings("unchecked")
            List<String> scopes = claims.get("scopes", List.class);
            Number branchIdNum = claims.get("branchId", Number.class);
            UUID sessionId = claims.getId() == null ? null : UUID.fromString(claims.getId());
            Long merchantId = merchantIdNum == null ? null : merchantIdNum.longValue();
            if (merchantId == null && AuthSubject.TYPE_MERCHANT.equals(subjectType)) {
                merchantId = userId;
            }
            return Optional.of(new AuthPrincipal(
                    userId,
                    subjectType,
                    claims.getSubject(),
                    roles == null ? List.of() : List.copyOf(roles),
                    scopes == null ? List.of() : List.copyOf(scopes),
                    merchantId,
                    branchIdNum == null ? null : branchIdNum.longValue(),
                    sessionId
            ));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    private static Long resolveUserId(String subjectType, Number userId, Number merchantId) {
        if (userId != null) {
            return userId.longValue();
        }
        if (AuthSubject.TYPE_MERCHANT.equals(subjectType) && merchantId != null) {
            return merchantId.longValue();
        }
        return null;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
