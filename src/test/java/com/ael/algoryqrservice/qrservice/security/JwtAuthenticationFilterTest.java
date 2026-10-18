package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.adminservice.application.DashboardSessionService;
import com.ael.algoryqrservice.qrservice.account.service.JwtService;
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
    private final DashboardSessionService dashboardSessionService = mock(DashboardSessionService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, dashboardSessionService);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_whenMerchantSubject_thenSkipDashboardSessionCheck() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parseValidAccessToken("token")).thenReturn(Optional.of(claims));
        when(jwtService.isDashboardPrincipal(claims)).thenReturn(false);
        when(jwtService.extractSubjectType(claims)).thenReturn(JwtService.SUBJECT_MERCHANT);
        when(jwtService.extractMerchantId(claims)).thenReturn(42L);
        when(jwtService.extractEmail(claims)).thenReturn("merchant@example.com");
        when(jwtService.extractPrincipalType(claims)).thenReturn(JwtService.PRINCIPAL_APP);
        when(jwtService.extractRoles(claims)).thenReturn(List.of("ROLE_USER"));
        when(jwtService.extractScopes(claims)).thenReturn(List.of());
        when(jwtService.extractProducts(claims)).thenReturn(List.of());
        when(jwtService.extractActivePackage(claims)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(dashboardSessionService, never()).isSessionActive(org.mockito.ArgumentMatchers.any());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
                .isInstanceOf(JwtAccessPrincipal.class);
        JwtAccessPrincipal principal = (JwtAccessPrincipal) SecurityContextHolder.getContext().getAuthentication().getDetails();
        assertThat(principal.merchantId()).isEqualTo(42L);
    }
}
