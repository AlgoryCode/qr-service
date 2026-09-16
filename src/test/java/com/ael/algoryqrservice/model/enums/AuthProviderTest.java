package com.ael.algoryqrservice.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthProviderTest {

    @Test
    void isGoogle_whenGoogleFamily_thenTrue() {
        assertThat(AuthProvider.GOOGLE.isGoogle()).isTrue();
        assertThat(AuthProvider.MOBILE_GOOGLE.isGoogle()).isTrue();
        assertThat(AuthProvider.BASIC.isGoogle()).isFalse();
    }
}
