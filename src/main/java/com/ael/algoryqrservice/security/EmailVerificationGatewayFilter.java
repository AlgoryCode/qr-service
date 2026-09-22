package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.exception.AuthErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmailVerificationGatewayFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_PREFIXES = List.of(
            "/auth/",
            "/account/email-verification",
            "/customer/",
            "/waiter/",
            "/admin/",
            "/google-auth/",
            "/oauth2/",
            "/actuator/",
            "/healthcheck",
            "/error"
    );

    private final EmailVerificationGate emailVerificationGate;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return isAllowed(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Long userId = ownerUserId();
        if (userId == null || !emailVerificationGate.isVerificationPending(userId)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", AuthErrorCodes.EMAIL_NOT_VERIFIED,
                "message", AuthErrorCodes.EMAIL_NOT_VERIFIED_MESSAGE
        ));
    }

    static boolean isAllowed(String requestUri) {
        if (requestUri == null || requestUri.isBlank() || "/".equals(requestUri)) {
            return true;
        }
        return ALLOWED_PREFIXES.stream().anyMatch(requestUri::startsWith);
    }

    private static Long ownerUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (!(authentication.getDetails() instanceof JwtAccessPrincipal principal)) {
            return null;
        }
        if (principal.isCustomer() || principal.isWaiter() || principal.isDemo()) {
            return null;
        }
        return principal.userId();
    }
}
