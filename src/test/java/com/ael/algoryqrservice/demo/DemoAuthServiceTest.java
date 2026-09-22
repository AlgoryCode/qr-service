package com.ael.algoryqrservice.demo;

import com.ael.algoryqrservice.config.JwtProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.LoginRequest;
import com.ael.algoryqrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.security.JwtAccessPrincipal;
import com.ael.algoryqrservice.service.JwtService;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoAuthServiceTest {

    private static final String EMAIL = "demo@algorycode.com";
    private static final String PASSWORD = "demo-secret";

    private UserRepository userRepository;
    private DemoAuthService authService;
    private DemoRequestFilter filter;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        DemoProperties properties = new DemoProperties();
        properties.setEnabled(true);
        properties.setEmail(EMAIL);
        properties.setPassword(PASSWORD);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("algoryqr-service-jwt-secret-key-min-32-chars-long");
        jwtProperties.setAccessExpirationMs(900_000);
        jwtProperties.setRefreshExpirationMs(604_800_000);
        jwtService = new JwtService(jwtProperties);
        DemoSessionStore sessions = new DemoSessionStore(jwtService, jwtProperties);
        userRepository = mock(UserRepository.class);
        authService = new DemoAuthService(new DemoCredentials(properties), sessions, userRepository, jwtService);
        filter = new DemoRequestFilter(jwtService, sessions);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginIfDemo_whenPasswordDoesNotMatch_thenEmpty() {
        LoginRequest request = login("other-secret");

        assertThat(authService.loginIfDemo(request, clientInfo())).isEmpty();
        verify(userRepository, never()).findByEmail(EMAIL);
    }

    @Test
    void loginIfDemo_whenUserMissing_thenBadRequest() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginIfDemo(login(PASSWORD), clientInfo()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void loginIfDemo_whenCredentialsMatch_thenIssuesDemoSession() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(User.builder().id(5L).email(EMAIL).build()));

        AuthResponse response = authService.loginIfDemo(login(PASSWORD), clientInfo()).orElseThrow();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + response.getAccessToken());
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(authService.isCurrentDemo()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
                .isInstanceOf(JwtAccessPrincipal.class);
        assertThat(authService.refreshIfDemo(response.getRefreshToken())).isPresent();
        LogoutRequest logout = new LogoutRequest();
        logout.setRefreshToken(response.getRefreshToken());
        assertThat(authService.logoutIfDemo(response.getAccessToken(), logout)).isTrue();
        assertThat(authService.listCurrentSessions(null).getTotalElements()).isEqualTo(1);
    }

    private static LoginRequest login(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(password);
        return request;
    }

    private static ClientInfo clientInfo() {
        return new ClientInfo("127.0.0.1", "ua", "device", "desktop");
    }
}
