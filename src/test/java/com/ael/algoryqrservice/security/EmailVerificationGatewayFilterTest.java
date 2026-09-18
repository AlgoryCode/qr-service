package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.exception.AuthErrorCodes;
import com.ael.algoryqrservice.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationGatewayFilterTest {

    private EmailVerificationGate gate;
    private ObjectMapper objectMapper;
    private EmailVerificationGatewayFilter filter;

    @BeforeEach
    void setUp() {
        gate = mock(EmailVerificationGate.class);
        objectMapper = new ObjectMapper();
        filter = new EmailVerificationGatewayFilter(gate, objectMapper);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilter_whenAuthOrVerificationPath_thenSkip() {
        assertThat(filter.shouldNotFilter(request("/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/account/email-verification/verify"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/waiter/orders"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/menus"))).isFalse();
    }

    @Test
    void doFilter_whenVerificationPending_thenForbiddenWithCode() throws Exception {
        authenticateOwner(7L);
        when(gate.isVerificationPending(7L)).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request("/menus"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(response.getContentAsByteArray(), Map.class);
        assertThat(body.get("code")).isEqualTo(AuthErrorCodes.EMAIL_NOT_VERIFIED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_whenVerified_thenContinue() throws Exception {
        authenticateOwner(7L);
        when(gate.isVerificationPending(7L)).thenReturn(false);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest servletRequest = request("/menus");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(servletRequest, response, chain);

        verify(chain).doFilter(servletRequest, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_whenUnauthenticated_thenContinueWithoutGateLookup() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest servletRequest = request("/menu/public/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(servletRequest, response, chain);

        verify(chain).doFilter(servletRequest, response);
        verify(gate, never()).isVerificationPending(any());
    }

    @Test
    void doFilter_whenCustomerPrincipal_thenContinueWithoutGateLookup() throws Exception {
        authenticatePrincipal(7L, JwtService.PRINCIPAL_CUSTOMER);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest servletRequest = request("/menus");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(servletRequest, response, chain);

        verify(chain).doFilter(servletRequest, response);
        verify(gate, never()).isVerificationPending(any());
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static void authenticateOwner(Long userId) {
        authenticatePrincipal(userId, JwtService.PRINCIPAL_APP);
    }

    private static void authenticatePrincipal(Long userId, String principalType) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of());
        authentication.setDetails(new JwtAccessPrincipal(userId, List.of(), List.of(), null, principalType));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
