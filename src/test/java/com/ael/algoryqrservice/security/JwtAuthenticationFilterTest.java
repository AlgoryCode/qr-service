package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.service.CustomerSessionService;
import com.ael.algoryqrservice.service.DashboardSessionService;
import com.ael.algoryqrservice.service.JwtService;
import com.ael.algoryqrservice.service.MenuWaiterSessionService;
import com.ael.algoryqrservice.service.SessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtService,
            sessionService,
            mock(DashboardSessionService.class),
            mock(CustomerSessionService.class),
            mock(MenuWaiterSessionService.class),
            mock(AccessTokenBlacklistService.class)
    );

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_whenAuthServiceSubject_thenSkipLocalSession() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parseValidAccessToken("token")).thenReturn(Optional.of(claims));
        when(jwtService.isAuthServiceSubject(claims)).thenReturn(true);
        when(jwtService.extractPackageOwnerId(claims)).thenReturn(42L);
        when(jwtService.extractEmail(claims)).thenReturn("merchant@example.com");
        when(jwtService.extractPrincipalType(claims)).thenReturn(JwtService.PRINCIPAL_APP);
        when(jwtService.extractRoles(claims)).thenReturn(List.of("ROLE_USER"));
        when(jwtService.extractScopes(claims)).thenReturn(List.of());
        when(jwtService.extractProducts(claims)).thenReturn(List.of());
        when(jwtService.extractActivePackage(claims)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(sessionService, never()).isSessionActive(org.mockito.ArgumentMatchers.any());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
                .isInstanceOf(JwtAccessPrincipal.class);
        JwtAccessPrincipal principal = (JwtAccessPrincipal) SecurityContextHolder.getContext().getAuthentication().getDetails();
        assertThat(principal.userId()).isEqualTo(42L);
    }
}
