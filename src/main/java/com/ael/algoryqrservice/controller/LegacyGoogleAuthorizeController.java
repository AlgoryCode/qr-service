package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.config.AuthServiceClientProperties;
import com.ael.algoryqrservice.security.GoogleOAuthPaths;
import com.ael.algoryqrservice.security.LegacyGoogleAuthorizeRedirect;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class LegacyGoogleAuthorizeController {

    private final AuthServiceClientProperties authService;

    @GetMapping({GoogleOAuthPaths.AUTHORIZE, GoogleOAuthPaths.LEGACY_AUTHORIZE})
    public void authorize(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(LegacyGoogleAuthorizeRedirect.target(
                authService.getPublicBaseUrl(),
                request.getServerName(),
                request.getQueryString()
        ));
    }
}
