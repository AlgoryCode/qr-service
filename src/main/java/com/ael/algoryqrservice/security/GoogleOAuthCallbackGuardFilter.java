package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.model.enums.GoogleAuthErrorCode;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.service.GoogleAuthRedirectService;
import com.ael.algoryqrservice.service.GoogleAuthSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class GoogleOAuthCallbackGuardFilter extends OncePerRequestFilter {

    private final GoogleAuthRedirectService redirectService;
    private final GoogleAuthSessionService sessionService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GoogleOAuthPaths.CALLBACK.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String code = request.getParameter("code");
        if (code != null && !code.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        GoogleAuthIntent intent = sessionService.findIntent(request).orElse(null);
        sessionService.clear(request);

        GoogleAuthErrorCode errorCode = resolveErrorCode(request.getParameter("error"));
        String target = redirectService.failureUrl(intent, errorCode);
        if (redirectService.targetsThisCallback(request, target)) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "GOOGLE_FRONTEND_CALLBACK_URL API OAuth callback yolunu göstermemelidir"
            );
            return;
        }
        response.sendRedirect(target);
    }

    private GoogleAuthErrorCode resolveErrorCode(String error) {
        if (error == null || error.isBlank()) {
            return GoogleAuthErrorCode.OAUTH_FAILED;
        }
        String normalized = error.trim().toLowerCase();
        if ("access_denied".equals(normalized)) {
            return GoogleAuthErrorCode.ACCESS_DENIED;
        }
        for (GoogleAuthErrorCode code : GoogleAuthErrorCode.values()) {
            if (code.value().equals(normalized)) {
                return code;
            }
        }
        return GoogleAuthErrorCode.OAUTH_FAILED;
    }
}
