package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.GoogleOidcIdentity;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIdTokenAuthServiceTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Mock
    private GoogleOAuthUserService googleOAuthUserService;
    @Mock
    private SessionService sessionService;

    @InjectMocks
    private GoogleIdTokenAuthService googleIdTokenAuthService;

    @Test
    void authenticate_whenLoginTokenIsValid_thenReturnSession() {
        GoogleOidcIdentity identity = new GoogleOidcIdentity("sub-1", "user@example.com", "Ada", "Lovelace", true);
        User user = User.builder()
                .id(7L)
                .email("user@example.com")
                .role(UserRole.USER)
                .provider(AuthProvider.MOBILE_GOOGLE)
                .providerSubject("sub-1")
                .build();
        ClientInfo clientInfo = new ClientInfo("127.0.0.1", "Android", "Pixel", "MOBILE");
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(identity);
        when(googleOAuthUserService.resolve(
                GoogleAuthIntent.LOGIN,
                identity,
                clientInfo,
                AuthProvider.MOBILE_GOOGLE
        )).thenReturn(user);
        when(sessionService.createSession(user, clientInfo, AuthProvider.MOBILE_GOOGLE))
                .thenReturn(new SessionService.SessionTokens(null, "access", "refresh", user));
        when(sessionService.buildAuthResponse("access", "refresh"))
                .thenReturn(AuthResponse.builder().accessToken("access").refreshToken("refresh").build());

        AuthResponse response = googleIdTokenAuthService.authenticate("id-token", GoogleAuthIntent.LOGIN, clientInfo);

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        verify(sessionService).createSession(user, clientInfo, AuthProvider.MOBILE_GOOGLE);
    }

    @Test
    void authenticate_whenCustomerIntent_thenReject() {
        assertThatThrownBy(() -> googleIdTokenAuthService.authenticate(
                "id-token",
                GoogleAuthIntent.CUSTOMER_LOGIN,
                new ClientInfo("127.0.0.1", "Android", "Pixel", "MOBILE")
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("geçersiz");
        verifyNoInteractions(googleIdTokenVerifier, googleOAuthUserService, sessionService);
    }

    @Test
    void authenticate_whenVerifierRejectsToken_thenPropagate() {
        ClientInfo clientInfo = new ClientInfo("127.0.0.1", "Android", "Pixel", "MOBILE");
        when(googleIdTokenVerifier.verify("bad-token"))
                .thenThrow(new UnauthorizedException("Google kimlik doğrulaması tamamlanamadı"));

        assertThatThrownBy(() -> googleIdTokenAuthService.authenticate(
                "bad-token",
                GoogleAuthIntent.LOGIN,
                clientInfo
        ))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Google kimlik doğrulaması");
        verifyNoInteractions(googleOAuthUserService, sessionService);
    }
}
