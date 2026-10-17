package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

public class GoogleIdTokenVerifier {

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER_HTTPS = "https://accounts.google.com";
    private static final String ISSUER_ACCOUNTS = "accounts.google.com";

    private final JwtDecoder jwtDecoder;
    private final Set<String> acceptedAudiences;

    public GoogleIdTokenVerifier(JwtDecoder jwtDecoder, Set<String> acceptedAudiences) {
        this.jwtDecoder = jwtDecoder;
        this.acceptedAudiences = acceptedAudiences;
    }

    public static GoogleIdTokenVerifier fromClientIds(String webClientId, String androidClientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        decoder.setJwtValidator(jwt -> {
            OAuth2TokenValidatorResult timestamp = JwtValidators.createDefault().validate(jwt);
            if (timestamp.hasErrors()) {
                return timestamp;
            }
            return OAuth2TokenValidatorResult.success();
        });
        return new GoogleIdTokenVerifier(decoder, audiences(webClientId, androidClientId));
    }

    public GoogleOidcIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new UnauthorizedException("Google kimlik jetonu zorunludur");
        }
        Jwt jwt = decode(idToken.trim());
        requireIssuer(jwt);
        requireAudience(jwt);
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new UnauthorizedException("Google kimlik doğrulaması tamamlanamadı");
        }
        return new GoogleOidcIdentity(
                subject,
                email.trim().toLowerCase(Locale.ROOT),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                emailVerified(jwt)
        );
    }

    private Jwt decode(String idToken) {
        try {
            return jwtDecoder.decode(idToken);
        } catch (JwtException exception) {
            throw new UnauthorizedException("Google kimlik doğrulaması tamamlanamadı");
        }
    }

    private void requireIssuer(Jwt jwt) {
        String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
        if (!ISSUER_HTTPS.equals(issuer) && !ISSUER_ACCOUNTS.equals(issuer)) {
            throw new UnauthorizedException("Google kimlik doğrulaması tamamlanamadı");
        }
    }

    private void requireAudience(Jwt jwt) {
        Collection<String> audience = jwt.getAudience();
        if (audience == null || audience.stream().noneMatch(acceptedAudiences::contains)) {
            throw new UnauthorizedException("Google kimlik doğrulaması tamamlanamadı");
        }
    }

    private static boolean emailVerified(Jwt jwt) {
        Boolean verified = jwt.getClaimAsBoolean("email_verified");
        if (verified != null) {
            return verified;
        }
        return "true".equalsIgnoreCase(jwt.getClaimAsString("email_verified"));
    }

    private static Set<String> audiences(String webClientId, String androidClientId) {
        java.util.LinkedHashSet<String> audiences = new java.util.LinkedHashSet<>();
        if (webClientId != null && !webClientId.isBlank()) {
            audiences.add(webClientId.trim());
        }
        if (androidClientId != null && !androidClientId.isBlank()) {
            audiences.add(androidClientId.trim());
        }
        return Set.copyOf(audiences);
    }
}
