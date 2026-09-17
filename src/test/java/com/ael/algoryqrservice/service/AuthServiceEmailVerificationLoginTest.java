package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.repository.DashboardUserRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
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

    @Mock
    UserRepository userRepository;
    @Mock
    DashboardUserRepository dashboardUserRepository;
    @Mock
    MenuWaiterRepository menuWaiterRepository;
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

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                dashboardUserRepository,
                menuWaiterRepository,
                passwordEncoder,
                authenticationManager,
                sessionService,
                jwtService,
                packageActivationService,
                userAccessProfileService,
                emailVerificationService
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
        when(menuWaiterRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret");

        assertThatThrownBy(() -> authService.login(request, new ClientInfo("127.0.0.1", "ua", "device", "desktop")))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(ex -> {
                    ForbiddenException forbidden = (ForbiddenException) ex;
                    assertThat(forbidden.getCode()).isEqualTo(AuthService.EMAIL_NOT_VERIFIED_CODE);
                });

        verify(packageActivationService, never()).ensureSubscriptionState(any());
        verify(sessionService, never()).createSession(any(), any());
    }
}
