package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.GoogleOAuthProperties;
import com.ael.algoryqrservice.model.enums.GoogleAuthErrorCode;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.security.GoogleOAuthPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

@Service
@RequiredArgsConstructor
public class GoogleAuthRedirectService {

    private final GoogleOAuthProperties properties;

    public void success(HttpServletResponse response, GoogleAuthIntent intent, String ticket) throws IOException {
        response.sendRedirect(successUrl(intent, ticket));
    }

    public void failure(
            HttpServletResponse response,
            GoogleAuthIntent intent,
            GoogleAuthErrorCode errorCode
    ) throws IOException {
        response.sendRedirect(failureUrl(intent, errorCode));
    }

    public String successUrl(GoogleAuthIntent intent, String ticket) {
        return UriComponentsBuilder
                .fromUriString(properties.frontendCallbackUrl())
                .queryParam("ticket", ticket)
                .queryParam("intent", intent.value())
                .build(true)
                .toUriString();
    }

    public String failureUrl(GoogleAuthIntent intent, GoogleAuthErrorCode errorCode) {
        GoogleAuthIntent resolvedIntent = intent != null ? intent : GoogleAuthIntent.LOGIN;
        return UriComponentsBuilder
                .fromUriString(properties.frontendCallbackUrl())
                .queryParam("error", errorCode.value())
                .queryParam("intent", resolvedIntent.value())
                .build(true)
                .toUriString();
    }

    public boolean targetsThisCallback(HttpServletRequest request, String targetUrl) {
        try {
            URI target = URI.create(targetUrl);
            String targetPath = normalizePath(target.getPath());
            if (!GoogleOAuthPaths.CALLBACK.equals(targetPath)
                    && !GoogleOAuthPaths.LEGACY_CALLBACK.equals(targetPath)) {
                return false;
            }
            String targetHost = target.getHost();
            if (targetHost == null || targetHost.isBlank()) {
                return true;
            }
            return targetHost.equalsIgnoreCase(request.getServerName());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
