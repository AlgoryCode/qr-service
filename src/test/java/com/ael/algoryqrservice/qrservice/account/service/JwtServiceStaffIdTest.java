package com.ael.algoryqrservice.qrservice.account.service;

import com.ael.algoryqrservice.qrservice.config.JwtProperties;
import com.ael.algoryqrservice.authservice.security.JwtTokenCodec;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class JwtServiceStaffIdTest {

    @Test
    void extractStaffId_fallsBackToUserIdForStaffSubjectType() {
        JwtTokenCodec codec = new JwtTokenCodec("test-only-jwt-secret-0123456789abcdef0123456789abcdef", "algoryqr", "algoryqr-api");
        JwtService jwtService = new JwtService(mock(JwtProperties.class), codec);

        Date now = new Date();
        String token = codec.builder(JwtTokenCodec.TYPE_ACCESS)
                .subject("waiter1")
                .claim("subjectType", JwtService.SUBJECT_STAFF)
                .claim("userId", 42)
                .claim("merchantId", 1)
                .claim("branchId", 10)
                .claim("roles", List.of("ROLE_WAITER"))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .id("00000000-0000-0000-0000-000000000001")
                .compact();

        var claims = jwtService.parseValidAccessToken(token).orElseThrow();
        assertEquals(42L, jwtService.extractStaffId(claims));
    }
}
