package com.ael.algoryqrservice.qrservice.service;

import com.ael.algoryqrservice.authservice.service.GoogleAuthRedirectService;
import com.ael.algoryqrservice.authservice.config.GoogleOAuthProperties;
import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthErrorCode;
import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthIntent;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthRedirectServiceTest {

    @Mock
    private HttpServletRequest request;

    private GoogleAuthRedirectService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthRedirectService(new GoogleOAuthProperties(
                "http://localhost:3000/api/auth/google/callback",
                Duration.ofMinutes(2),
                null,
                null
        ));
    }

    @Test
    void successUrl_whenLogin_thenIncludesTicketAndIntent() {
        String url = service.successUrl(GoogleAuthIntent.LOGIN, "ticket-1");

        assertThat(url).isEqualTo(
                "http://localhost:3000/api/auth/google/callback?ticket=ticket-1&intent=login"
        );
    }

    @Test
    void failureUrl_whenAccountNotFound_thenIncludesErrorAndIntent() {
        String url = service.failureUrl(GoogleAuthIntent.REGISTER, GoogleAuthErrorCode.ACCOUNT_NOT_FOUND);

        assertThat(url).isEqualTo(
                "http://localhost:3000/api/auth/google/callback?error=account_not_found&intent=register"
        );
    }

    @Test
    void failureUrl_whenIntentNull_thenDefaultsToLogin() {
        String url = service.failureUrl(null, GoogleAuthErrorCode.OAUTH_FAILED);

        assertThat(url).isEqualTo(
                "http://localhost:3000/api/auth/google/callback?error=oauth_failed&intent=login"
        );
    }

}
