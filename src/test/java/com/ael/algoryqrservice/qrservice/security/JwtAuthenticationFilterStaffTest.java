package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.qrservice.service.JwtService;
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
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterStaffTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void staffToken_keepsStaffIdAndWaiterPrincipal() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        Claims claims = mock(Claims.class);
        when(jwtService.parseValidAccessToken("token")).thenReturn(Optional.of(claims));
        when(jwtService.extractEmail(claims)).thenReturn("tarik");
        when(jwtService.extractPrincipalType(claims)).thenReturn(JwtService.PRINCIPAL_APP);
        when(jwtService.extractSubjectType(claims)).thenReturn(JwtService.SUBJECT_STAFF);
        when(jwtService.extractMerchantId(claims)).thenReturn(118L);
        when(jwtService.extractUserId(claims)).thenReturn(57L);
        when(jwtService.extractRoles(claims)).thenReturn(List.of("ROLE_KITCHEN"));
        when(jwtService.extractBranchId(claims)).thenReturn(37L);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        FilterChain chain = (servletRequest, servletResponse) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getDetails()).isInstanceOf(JwtAccessPrincipal.class);
            JwtAccessPrincipal principal = (JwtAccessPrincipal) authentication.getDetails();
            assertThat(principal.userId()).isEqualTo(57L);
            assertThat(principal.merchantId()).isEqualTo(118L);
            assertThat(principal.branchId()).isEqualTo(37L);
            assertThat(principal.isWaiter()).isTrue();
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }
}
