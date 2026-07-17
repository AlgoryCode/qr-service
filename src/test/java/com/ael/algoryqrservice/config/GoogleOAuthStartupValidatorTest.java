package com.ael.algoryqrservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleOAuthStartupValidatorTest {

    @Test
    void run_whenFrontendPointsToApiCallback_thenFail() {
        GoogleOAuthStartupValidator validator = new GoogleOAuthStartupValidator(
                new GoogleOAuthProperties("http://localhost:8055/google-auth/callback", Duration.ofMinutes(2))
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_FRONTEND_CALLBACK_URL");
    }

    @Test
    void run_whenFrontendPointsToLegacyApiCallback_thenFail() {
        GoogleOAuthStartupValidator validator = new GoogleOAuthStartupValidator(
                new GoogleOAuthProperties("http://localhost:8055/auth/google/callback", Duration.ofMinutes(2))
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_FRONTEND_CALLBACK_URL");
    }

    @Test
    void run_whenFrontendMissingApiPrefix_thenFail() {
        GoogleOAuthStartupValidator validator = new GoogleOAuthStartupValidator(
                new GoogleOAuthProperties("http://localhost:3000/auth/google/callback", Duration.ofMinutes(2))
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_FRONTEND_CALLBACK_URL");
    }

    @Test
    void run_whenFrontendIsNextCallback_thenPass() {
        GoogleOAuthStartupValidator validator = new GoogleOAuthStartupValidator(
                new GoogleOAuthProperties(
                        "http://localhost:3000/api/auth/google/callback",
                        Duration.ofMinutes(2)
                )
        );

        assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }
}
