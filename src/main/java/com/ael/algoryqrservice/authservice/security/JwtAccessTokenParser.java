package com.ael.algoryqrservice.authservice.security;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAccessTokenParser {

    private final JwtTokenCodec tokenCodec;

    public Optional<AuthPrincipal> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = tokenCodec.parse(token, JwtTokenCodec.TYPE_ACCESS);
            String subjectType = claims.get("subjectType", String.class);
            Long merchantId = longClaim(claims, "merchantId");
            Long staffId = staffIdClaim(claims, subjectType);
            Long customerId = longClaim(claims, "customerId");
            Long authSubjectId = longClaim(claims, "authSubjectId");
            if (!hasIdentity(subjectType, merchantId, staffId, customerId, authSubjectId)) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            @SuppressWarnings("unchecked")
            List<String> scopes = claims.get("scopes", List.class);
            Number branchIdNum = claims.get("branchId", Number.class);
            UUID sessionId = claims.getId() == null ? null : UUID.fromString(claims.getId());
            return Optional.of(new AuthPrincipal(
                    subjectType,
                    claims.getSubject(),
                    roles == null ? List.of() : List.copyOf(roles),
                    scopes == null ? List.of() : List.copyOf(scopes),
                    merchantId,
                    staffId,
                    customerId,
                    authSubjectId,
                    branchIdNum == null ? null : branchIdNum.longValue(),
                    sessionId
            ));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    private static boolean hasIdentity(
            String subjectType,
            Long merchantId,
            Long staffId,
            Long customerId,
            Long authSubjectId
    ) {
        if (AuthSubject.TYPE_MERCHANT.equals(subjectType)) {
            return merchantId != null || authSubjectId != null;
        }
        if (AuthSubject.TYPE_STAFF.equals(subjectType)) {
            return staffId != null;
        }
        if (AuthSubject.TYPE_CUSTOMER.equals(subjectType)) {
            return customerId != null;
        }
        if (AuthSubject.TYPE_ADMIN.equals(subjectType)) {
            return authSubjectId != null;
        }
        return merchantId != null || staffId != null || customerId != null || authSubjectId != null;
    }

    private static Long longClaim(Claims claims, String name) {
        Number value = claims.get(name, Number.class);
        return value == null ? null : value.longValue();
    }

    private static Long staffIdClaim(Claims claims, String subjectType) {
        Long staffId = longClaim(claims, "staffId");
        if (staffId != null) {
            return staffId;
        }
        if (AuthSubject.TYPE_STAFF.equals(subjectType)) {
            return longClaim(claims, "userId");
        }
        return null;
    }

}
