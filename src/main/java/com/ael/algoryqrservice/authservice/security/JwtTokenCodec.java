package com.ael.algoryqrservice.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * The single place JWTs are signed and verified. Every token carries and is checked for
 * issuer, audience and type, so a token minted for one purpose (e.g. refresh) or by another
 * system sharing the key is never accepted as something else.
 *
 * <p>Startup fails when the signing secret is missing, shorter than 256 bits, or one of the
 * defaults that used to ship in the repository: with a known HMAC key anyone can mint tokens.
 */
@Component
public class JwtTokenCodec {

    public static final String TYPE_CLAIM = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final int MIN_SECRET_BYTES = 32;
    /** Values that were committed as defaults or examples; they must never sign real tokens. */
    private static final Set<String> KNOWN_PUBLIC_SECRETS = Set.of(
            "algoryqr-service-jwt-secret-key-min-32-chars-long",
            "change-me-min-32-chars"
    );

    private final SecretKey key;
    private final String issuer;
    private final String audience;

    public JwtTokenCodec(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.issuer:algoryqr}") String issuer,
            @Value("${jwt.audience:algoryqr-api}") String audience
    ) {
        this.key = Keys.hmacShaKeyFor(requireStrongSecret(secret).getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
    }

    static String requireStrongSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set: refusing to start without a token signing key");
        }
        if (KNOWN_PUBLIC_SECRETS.contains(secret.trim())) {
            throw new IllegalStateException("JWT_SECRET uses a publicly known default value: set a fresh random secret");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        return secret;
    }

    /** A builder pre-filled with issuer, audience, type and the signing key. */
    public JwtBuilder builder(String type) {
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .claim(TYPE_CLAIM, type)
                .signWith(key);
    }

    /**
     * Verifies signature, expiry, issuer, audience and type.
     *
     * @throws JwtException when any check fails
     */
    public Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String type = claims.get(TYPE_CLAIM, String.class);
        if (!expectedType.equals(type)) {
            throw new IncorrectClaimException(null, claims, TYPE_CLAIM, expectedType, "Unexpected token type: " + type);
        }
        return claims;
    }
}
