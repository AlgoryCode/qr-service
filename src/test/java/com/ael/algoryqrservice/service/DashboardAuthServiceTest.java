package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.DashboardUser;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.model.enums.DashboardRole;
import com.ael.algoryqrservice.repository.DashboardUserRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAuthServiceTest {

    private static final ClientInfo CLIENT_INFO = new ClientInfo("127.0.0.1", "ua", "device", "browser");

    @Mock
    private DashboardUserRepository dashboardUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private DashboardSessionService dashboardSessionService;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private DashboardAuthService dashboardAuthService;

    @Test
    void login_whenCredentialsValid_thenCreateDashboardSession() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.com");
        request.setPassword("secret");
        DashboardUser user = DashboardUser.builder()
                .id(1L)
                .email("admin@example.com")
                .password("hash")
                .role(DashboardRole.ADMIN)
                .active(true)
                .build();
        AuthResponse expected = AuthResponse.builder().accessToken("a").refreshToken("r").build();

        when(dashboardUserRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(dashboardSessionService.createSession(user, CLIENT_INFO)).thenReturn(expected);

        AuthResponse result = dashboardAuthService.login(request, CLIENT_INFO);

        assertThat(result.getAccessToken()).isEqualTo("a");
        verify(dashboardSessionService).createSession(user, CLIENT_INFO);
    }

    @Test
    void login_whenUserInactive_thenForbidden() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.com");
        request.setPassword("secret");
        DashboardUser user = DashboardUser.builder()
                .email("admin@example.com")
                .password("hash")
                .active(false)
                .build();
        when(dashboardUserRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> dashboardAuthService.login(request, CLIENT_INFO))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void login_whenPasswordWrong_thenBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.com");
        request.setPassword("wrong");
        DashboardUser user = DashboardUser.builder()
                .email("admin@example.com")
                .password("hash")
                .active(true)
                .build();
        when(dashboardUserRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> dashboardAuthService.login(request, CLIENT_INFO))
                .isInstanceOf(BadCredentialsException.class);
    }
}
