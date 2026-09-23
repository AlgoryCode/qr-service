package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.AuthErrorCodes;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.exception.TooManyRequestsException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.repository.DashboardUserRepository;
import com.ael.algoryqrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.security.LoginAttemptGuard;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceEmailVerificationLoginTest {

    private static final int LOGIN_MAX_FAILURES = 3;

    @Mock
    UserRepository userRepository;
    @Mock
    DashboardUserRepository dashboardUserRepository;
    @Mock MerchantStaffRepository merchantStaffRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    SessionService sessionService;
    @Mock
    JwtService jwtService;
    @Mock
    PackageActivationService packageActivationService;
    @Mock
    UserAccessProfileService userAccessProfileService;
    @Mock
    EmailVerificationService emailVerificationService;
    @Mock
    com.ael.algoryqrservice.demo.DemoAuthService demoAuthService;

    LoginAttemptGuard loginAttemptGuard;

    AuthService authService;

    @BeforeEach
    void setUp() {
        loginAttemptGuard = new LoginAttemptGuard(LOGIN_MAX_FAILURES, 15);
        when(demoAuthService.loginIfDemo(any(), any())).thenReturn(Optional.empty());
        authService = new AuthService(
                userRepository,
                dashboardUserRepository,
                merchantStaffRepository,
                passwordEncoder,
                authenticationManager,
                sessionService,
                jwtService,
                packageActivationService,
                userAccessProfileService,
                emailVerificationService,
                loginAttemptGuard,
                demoAuthService
        );
    }

    @Test
    void login_whenBasicEmailNotVerified_thenForbidden() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .provider(AuthProvider.BASIC)
                .role(UserRole.USER)
                .emailVerified(false)
                .build();
        when(dashboardUserRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(merchantStaffRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret");

        assertThatThrownBy(() -> authService.login(request, new ClientInfo("127.0.0.1", "ua", "device", "desktop")))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(ex -> {
                    ForbiddenException forbidden = (ForbiddenException) ex;
                    assertThat(forbidden.getCode()).isEqualTo(AuthErrorCodes.EMAIL_NOT_VERIFIED);
                });

        verify(packageActivationService, never()).ensureSubscriptionState(any());
        verify(sessionService, never()).createSession(any(), any());
    }

    @Test
    void login_whenFailureLimitReached_thenTooManyRequests() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .provider(AuthProvider.BASIC)
                .role(UserRole.USER)
                .emailVerified(true)
                .build();
        when(dashboardUserRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(merchantStaffRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrong");
        ClientInfo clientInfo = new ClientInfo("127.0.0.1", "ua", "device", "desktop");

        for (int attempt = 0; attempt < LOGIN_MAX_FAILURES; attempt++) {
            assertThatThrownBy(() -> authService.login(request, clientInfo))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login(request, clientInfo))
                .isInstanceOf(TooManyRequestsException.class);

        verify(sessionService, never()).createSession(any(), any());
    }
}
