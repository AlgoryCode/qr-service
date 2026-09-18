package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.exception.AuthErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthRateLimitGatewayFilterTest {

    private static final int LOGIN_LIMIT = 10;

    private ObjectMapper objectMapper;
    private AuthRateLimitGatewayFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new AuthRateLimitGatewayFilter(objectMapper, true);
    }

    @Test
    void ruleKeyFor_whenAuthPath_thenLongestMatchWins() {
        assertThat(AuthRateLimitGatewayFilter.ruleKeyFor("/auth/login")).contains("/auth/login");
        assertThat(AuthRateLimitGatewayFilter.ruleKeyFor("/customer/auth/login"))
                .contains("/customer/auth/login");
        assertThat(AuthRateLimitGatewayFilter.ruleKeyFor("/auth/refresh")).isEmpty();
    }

    @Test
    void shouldNotFilter_whenUnrelatedOrNonPost_thenSkip() {
        assertThat(filter.shouldNotFilter(request("POST", "/menus"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/auth/login"))).isFalse();
    }

    @Test
    void shouldNotFilter_whenDisabled_thenSkip() {
        AuthRateLimitGatewayFilter disabled = new AuthRateLimitGatewayFilter(objectMapper, false);
        assertThat(disabled.shouldNotFilter(request("POST", "/auth/login"))).isTrue();
    }

    @Test
    void doFilter_whenLimitExceeded_thenTooManyRequests() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int attempt = 0; attempt < LOGIN_LIMIT; attempt++) {
            filter.doFilterInternal(loginRequest(), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();

        filter.doFilterInternal(loginRequest(), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("300");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(blocked.getContentAsByteArray(), Map.class);
        assertThat(body.get("code")).isEqualTo(AuthErrorCodes.TOO_MANY_REQUESTS);
        verify(chain, times(LOGIN_LIMIT)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilter_whenDifferentClientIp_thenCountedSeparately() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int attempt = 0; attempt < LOGIN_LIMIT; attempt++) {
            filter.doFilterInternal(loginRequest(), new MockHttpServletResponse(), chain);
        }
        MockHttpServletRequest otherClient = loginRequest();
        otherClient.addHeader("X-Forwarded-For", "203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(otherClient, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain, times(LOGIN_LIMIT + 1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static MockHttpServletRequest loginRequest() {
        return request("POST", "/auth/login");
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setRemoteAddr("198.51.100.4");
        return request;
    }
}
