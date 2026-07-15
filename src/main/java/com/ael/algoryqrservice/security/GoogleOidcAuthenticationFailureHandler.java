package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.service.GoogleAuthRedirectService;
import com.ael.algoryqrservice.service.GoogleAuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOidcAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final GoogleAuthSessionService authSessionService;
    private final GoogleAuthRedirectService redirectService;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        log.warn("Google OAuth kimlik doğrulaması başarısız: {}", exception.getMessage(), exception);
        authSessionService.clear(request);
        response.sendRedirect(redirectService.failure());
    }
}
