package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.exception.AuthErrorCodes;
import com.ael.algoryqrservice.util.RequestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class AuthRateLimitGatewayFilter extends OncePerRequestFilter {

    record Rule(int maxRequests, Duration window) {
    }

    private static final int MAX_TRACKED_CLIENTS = 100_000;
    private static final Map<String, Rule> RULES = Map.of(
            "/auth/login", new Rule(10, Duration.ofMinutes(5)),
            "/auth/register", new Rule(5, Duration.ofHours(1)),
            "/auth/email-verification/resend", new Rule(5, Duration.ofHours(1)),
            "/auth/email-verification/verify", new Rule(10, Duration.ofMinutes(15)),
            "/customer/auth/login", new Rule(10, Duration.ofMinutes(5)),
            "/customer/auth/register", new Rule(5, Duration.ofHours(1)),
            "/waiter/auth/login", new Rule(10, Duration.ofMinutes(5)),
            "/admin/auth/sessions", new Rule(10, Duration.ofMinutes(5))
    );

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Map<String, Cache<String, AtomicInteger>> counters;

    public AuthRateLimitGatewayFilter(
            ObjectMapper objectMapper,
            @Value("${app.auth-gateway.rate-limit-enabled:true}") boolean enabled
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.counters = RULES.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> Caffeine.newBuilder()
                        .expireAfterWrite(entry.getValue().window())
                        .maximumSize(MAX_TRACKED_CLIENTS)
                        .build()
        ));
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!enabled || !HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        return ruleKeyFor(request.getRequestURI()).isEmpty();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> ruleKey = ruleKeyFor(request.getRequestURI());
        if (ruleKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        Rule rule = RULES.get(ruleKey.get());
        String clientIp = RequestUtils.resolveClientIp(request);
        int hits = counters.get(ruleKey.get())
                .get(clientIp, key -> new AtomicInteger())
                .incrementAndGet();
        if (hits <= rule.maxRequests()) {
            filterChain.doFilter(request, response);
            return;
        }
        writeTooManyRequests(response, rule.window());
    }

    static Optional<String> ruleKeyFor(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return Optional.empty();
        }
        String normalized = requestUri.endsWith("/")
                ? requestUri.substring(0, requestUri.length() - 1)
                : requestUri;
        return RULES.keySet().stream()
                .filter(normalized::endsWith)
                .max(Comparator.comparingInt(String::length));
    }

    private void writeTooManyRequests(HttpServletResponse response, Duration window) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds()));
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", AuthErrorCodes.TOO_MANY_REQUESTS,
                "message", AuthErrorCodes.TOO_MANY_REQUESTS_MESSAGE
        ));
    }
}
