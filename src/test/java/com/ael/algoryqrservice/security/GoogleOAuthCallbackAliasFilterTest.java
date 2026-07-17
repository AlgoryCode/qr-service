package com.ael.algoryqrservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthCallbackAliasFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final GoogleOAuthCallbackAliasFilter filter = new GoogleOAuthCallbackAliasFilter();

    @Test
    void doFilter_whenLegacyCallback_thenRewriteUri() throws Exception {
        when(request.getRequestURI()).thenReturn(GoogleOAuthPaths.LEGACY_CALLBACK);
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("prod.qrapi.algorycode.com");
        when(request.getServerPort()).thenReturn(443);

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), eq(response));
        assertThat(captor.getValue().getRequestURI()).isEqualTo(GoogleOAuthPaths.CALLBACK);
        assertThat(captor.getValue().getServletPath()).isEqualTo(GoogleOAuthPaths.CALLBACK);
        assertThat(captor.getValue().getRequestURL().toString())
                .isEqualTo("https://prod.qrapi.algorycode.com" + GoogleOAuthPaths.CALLBACK);
    }

    @Test
    void doFilter_whenCanonicalCallback_thenPassThrough() throws Exception {
        when(request.getRequestURI()).thenReturn(GoogleOAuthPaths.CALLBACK);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
