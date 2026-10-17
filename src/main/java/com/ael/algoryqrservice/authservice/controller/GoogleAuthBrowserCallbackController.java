package com.ael.algoryqrservice.authservice.controller;

import com.ael.algoryqrservice.authservice.config.GoogleOAuthProperties;
import com.ael.algoryqrservice.authservice.service.GoogleAuthHandoffService;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import com.ael.algoryqrservice.qrservice.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

/**
 * Completes Google OAuth in-process when {@code GOOGLE_FRONTEND_CALLBACK_URL} points at qr-service
 * (e.g. {@code http://localhost:8080/api/auth/google/callback}), so the browser never depends on
 * a separate auth host for handoff redeem.
 */
@RestController
@RequiredArgsConstructor
public class GoogleAuthBrowserCallbackController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthBrowserCallbackController.class);

    private final GoogleAuthHandoffService handoffService;
    private final AppProperties appProperties;
    private final GoogleOAuthProperties googleOAuthProperties;

    @GetMapping({"/api/auth/google/callback", "/auth/google/callback"})
    public void browserCallback(
            @RequestParam(required = false) String ticket,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String intent,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        if (StringUtils.hasText(error)) {
            log.warn("Google OAuth browser callback error={} intent={}", error, intent);
            response.sendRedirect(failureRedirect(error, intent));
            return;
        }
        if (!StringUtils.hasText(ticket)) {
            log.warn("Google OAuth browser callback missing ticket and error");
            response.sendRedirect(failureRedirect("oauth_failed", intent));
            return;
        }
        try {
            Map<String, Object> tokens = handoffService.redeem(ticket, ClientInfo.from(request));
            response.sendRedirect(successRedirect(tokens, intent));
        } catch (RuntimeException exception) {
            log.warn("Google OAuth browser callback redeem failed: {}", exception.getMessage());
            response.sendRedirect(failureRedirect("oauth_failed", intent));
        }
    }

    private String successRedirect(Map<String, Object> tokens, String intent) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(trimSlash(appProperties.getUrl()))
                .path(normalizePath(googleOAuthProperties.postLoginRedirectPath()))
                .queryParam("accessToken", tokens.get("accessToken"))
                .queryParam("refreshToken", tokens.get("refreshToken"));
        if (StringUtils.hasText(intent)) {
            builder.queryParam("intent", intent.trim());
        }
        builder.queryParam("google", "success");
        String target = builder.build(true).toUriString();
        log.info("Google OAuth browser callback redirecting to frontend after successful redeem");
        return target;
    }

    private String failureRedirect(String error, String intent) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(trimSlash(appProperties.getUrl()))
                .path(normalizePath(googleOAuthProperties.postLoginRedirectPath()))
                .queryParam("error", error.trim().toLowerCase())
                .queryParam("google", "failed");
        if (StringUtils.hasText(intent)) {
            builder.queryParam("intent", intent.trim());
        }
        return builder.build(true).toUriString();
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:3000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/login";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
