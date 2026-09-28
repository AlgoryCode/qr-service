package com.ael.algoryqrservice.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyGoogleAuthorizeRedirectTest {

    @Test
    void target_whenStageHostAndNoConfiguredBase_thenRedirectsToStageAuth() {
        String location = LegacyGoogleAuthorizeRedirect.target(
                "",
                "stage.qrapi.algorycode.com",
                "intent=login"
        );

        assertThat(location).isEqualTo(
                "https://stage.auth.algorycode.com/api/v1/auth/google/authorize?intent=login"
        );
    }

    @Test
    void target_whenProdHost_thenRedirectsToProdAuth() {
        String location = LegacyGoogleAuthorizeRedirect.target(null, "prod.qrapi.algorycode.com", null);

        assertThat(location).isEqualTo("https://prod.auth.algorycode.com/api/v1/auth/google/authorize");
    }

    @Test
    void target_whenPublicBaseConfigured_thenUsesThatBase() {
        String location = LegacyGoogleAuthorizeRedirect.target(
                "https://stage.auth.algorycode.com/",
                "localhost",
                "intent=register"
        );

        assertThat(location).isEqualTo(
                "https://stage.auth.algorycode.com/api/v1/auth/google/authorize?intent=register"
        );
    }

    @Test
    void target_whenQueryContainsLineBreak_thenDropsQuery() {
        String location = LegacyGoogleAuthorizeRedirect.target(
                "",
                "stage.qrapi.algorycode.com",
                "intent=login\r\nSet-Cookie: x=y"
        );

        assertThat(location).isEqualTo("https://stage.auth.algorycode.com/api/v1/auth/google/authorize");
    }
}
