package com.ael.algoryqrservice.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenCodecTest {

    private static final String SECRET = "test-only-jwt-secret-0123456789abcdef0123456789abcdef";
    private final JwtTokenCodec codec = new JwtTokenCodec(SECRET, "algoryqr", "algoryqr-api");

    private String token(JwtTokenCodec signer, String type) {
        return signer.builder(type)
                .subject("owner@example.com")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();
    }

    @Test
    void parse_acceptsOwnTokenOfExpectedType() {
        Claims claims = codec.parse(token(codec, JwtTokenCodec.TYPE_ACCESS), JwtTokenCodec.TYPE_ACCESS);

        assertThat(claims.getSubject()).isEqualTo("owner@example.com");
        assertThat(claims.getIssuer()).isEqualTo("algoryqr");
        assertThat(claims.getAudience()).containsExactly("algoryqr-api");
    }

    @Test
    void parse_rejectsRefreshTokenUsedAsAccessToken() {
        String refresh = token(codec, JwtTokenCodec.TYPE_REFRESH);

        assertThatThrownBy(() -> codec.parse(refresh, JwtTokenCodec.TYPE_ACCESS)).isInstanceOf(JwtException.class);
    }

    @Test
    void parse_rejectsOtherAudienceOrIssuerWithSameKey() {
        JwtTokenCodec otherAudience = new JwtTokenCodec(SECRET, "algoryqr", "some-other-app");
        JwtTokenCodec otherIssuer = new JwtTokenCodec(SECRET, "someone-else", "algoryqr-api");

        assertThatThrownBy(() -> codec.parse(token(otherAudience, JwtTokenCodec.TYPE_ACCESS), JwtTokenCodec.TYPE_ACCESS))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> codec.parse(token(otherIssuer, JwtTokenCodec.TYPE_ACCESS), JwtTokenCodec.TYPE_ACCESS))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void constructor_refusesMissingShortOrPublicSecrets() {
        assertThatThrownBy(() -> new JwtTokenCodec("", "i", "a")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenCodec("too-short", "i", "a")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenCodec("algoryqr-service-jwt-secret-key-min-32-chars-long", "i", "a"))
                .isInstanceOf(IllegalStateException.class);
    }
}
