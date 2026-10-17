package com.ael.algoryqrservice.authservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Google OAuth redirect URIs are registered for {@code localhost}, but local clients often call
 * {@code 127.0.0.1}. Session and pending-intent cookies do not cross those hosts, so the callback
 * loses OAuth context. Normalize authorize entry points to {@code localhost} before cookies are set.
 */
@Component
public class GoogleOAuthLoopbackHostNormalizerFilter extends OncePerRequestFilter {

    private static final String AUTHORIZE_V1 = "/api/v1/auth/google/authorize";
    private static final String AUTHORIZE_LEGACY = "/auth/google/authorize";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod()) || !isAuthorizePath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String host = request.getServerName();
        if (!"127.0.0.1".equals(host) && !"0:0:0:0:0:0:0:1".equals(host) && !"[::1]".equals(host)) {
            filterChain.doFilter(request, response);
            return;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme(request.getScheme())
                .host("localhost")
                .port(request.getServerPort())
                .path(request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            builder.query(query);
        }
        response.sendRedirect(builder.build(true).toUriString());
    }

    private static boolean isAuthorizePath(String uri) {
        return AUTHORIZE_V1.equals(uri) || AUTHORIZE_LEGACY.equals(uri);
    }
}
