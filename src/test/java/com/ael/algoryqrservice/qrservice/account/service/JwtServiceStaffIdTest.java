package com.ael.algoryqrservice.qrservice.account.service;

import com.ael.algoryqrservice.qrservice.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceStaffIdTest {

    @Test
    void extractStaffId_fallsBackToUserIdForStaffSubjectType() {
        JwtProperties props = mock(JwtProperties.class);
        when(props.getSecret()).thenReturn("01234567890123456789012345678901");
        JwtService jwtService = new JwtService(props);

        SecretKey key = Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String token = Jwts.builder()
                .subject("waiter1")
                .claim("typ", "access")
                .claim("subjectType", JwtService.SUBJECT_STAFF)
                .claim("userId", 42)
                .claim("merchantId", 1)
                .claim("branchId", 10)
                .claim("roles", List.of("ROLE_WAITER"))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .id("00000000-0000-0000-0000-000000000001")
                .signWith(key)
                .compact();

        var claims = jwtService.parseValidAccessToken(token).orElseThrow();
        assertEquals(42L, jwtService.extractStaffId(claims));
    }
}
