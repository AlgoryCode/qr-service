package com.ael.algoryqrservice.adminservice.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B, SHA1 seed "12345678901234567890". */
    private static final String RFC_SECRET = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesRfcVectors() {
        assertThat(Totp.codeAt(RFC_SECRET, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.codeAt(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.codeAt(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
    }

    @Test
    void acceptsOneStepOfClockDriftOnly() {
        long now = 1_700_000_000L;
        String secret = Totp.newSecret();
        assertThat(Totp.verify(secret, Totp.codeAt(secret, now / 30), now)).isTrue();
        assertThat(Totp.verify(secret, Totp.codeAt(secret, now / 30 - 1), now)).isTrue();
        assertThat(Totp.verify(secret, Totp.codeAt(secret, now / 30 + 3), now)).isFalse();
        assertThat(Totp.verify(secret, "12 34", now)).isFalse();
        assertThat(Totp.verify(secret, null, now)).isFalse();
    }

    @Test
    void base32RoundTrips() {
        byte[] data = {0, 1, 2, (byte) 250, 99, 17, 3};
        assertThat(Totp.unbase32(Totp.base32(data))).containsExactly(data);
    }

    @Test
    void otpauthUriNamesIssuerAndAccount() {
        assertThat(Totp.otpauthUri("AlgoryQR Admin", "thamarat", "ABC"))
                .startsWith("otpauth://totp/")
                .contains("secret=ABC")
                .contains("issuer=AlgoryQR");
    }
}
