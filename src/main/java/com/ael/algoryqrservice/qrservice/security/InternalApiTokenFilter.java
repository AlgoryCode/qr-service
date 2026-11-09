package com.ael.algoryqrservice.qrservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Service-to-service endpoints under {@code /api/v1/internal/**} change credentials, staff, packages and merchants,
 * so the security chains leave them to this filter: the caller must send {@value #HEADER} with the shared token from
 * {@code INTERNAL_API_TOKEN}. Without a configured token every internal call is refused (fail closed).
 *
 * <p>Runs right after {@link LegacyApiPathCompatFilter}, so legacy {@code /internal/...} paths are already rewritten.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InternalApiTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Token";
    private static final String INTERNAL_PREFIX = "/api/v1/internal/";

    private final byte[] expectedToken;

    public InternalApiTokenFilter(@Value("${app.internal-api-token:}") String token) {
        this.expectedToken = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
        if (expectedToken == null) {
            log.warn("INTERNAL_API_TOKEN is not set: every /api/v1/internal request will be refused");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String sent = request.getHeader(HEADER);
        if (expectedToken != null && sent != null
                && MessageDigest.isEqual(expectedToken, sent.getBytes(StandardCharsets.UTF_8))) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("Internal API call refused path={} clientIp={}", request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"Internal API token required\"}");
    }
}
