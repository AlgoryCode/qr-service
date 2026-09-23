package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServicePackageOwnerTest {

    private final JwtService jwtService = new JwtService(mock(JwtProperties.class));

    @Test
    void extractPackageOwnerId_whenStaff_thenUseMerchantId() {
        Claims claims = subject(JwtService.SUBJECT_STAFF);
        when(claims.get("merchantId")).thenReturn(42L);
        when(claims.get("userId")).thenReturn(7L);

        assertThat(jwtService.extractPackageOwnerId(claims)).isEqualTo(42L);
    }

    @Test
    void extractPackageOwnerId_whenMerchant_thenUseUserId() {
        Claims claims = subject(JwtService.SUBJECT_MERCHANT);
        when(claims.get("userId")).thenReturn(7L);

        assertThat(jwtService.extractPackageOwnerId(claims)).isEqualTo(7L);
        assertThat(jwtService.isAuthServiceSubject(claims)).isTrue();
    }

    @Test
    void isAuthServiceSubject_whenPrincipalTypeOnly_thenFalse() {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.SUBJECT_TYPE_CLAIM, String.class)).thenReturn(null);

        assertThat(jwtService.isAuthServiceSubject(claims)).isFalse();
    }

    private Claims subject(String subjectType) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.SUBJECT_TYPE_CLAIM, String.class)).thenReturn(subjectType);
        return claims;
    }
}
