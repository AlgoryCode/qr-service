package com.ael.algoryqrservice.authservice.service;

import com.ael.algoryqrservice.authservice.config.GoogleOAuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthErrorCode;
import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthIntent;

@Service
@RequiredArgsConstructor
public class GoogleAuthRedirectService {

    private final GoogleOAuthProperties properties;

    public void success(HttpServletResponse response, GoogleAuthIntent intent, String ticket) throws IOException {
        response.sendRedirect(successUrl(intent, ticket));
    }

    public void failure(HttpServletResponse response, GoogleAuthIntent intent, GoogleAuthErrorCode errorCode)
            throws IOException {
        response.sendRedirect(failureUrl(intent, errorCode));
    }

    public String successUrl(GoogleAuthIntent intent, String ticket) {
        return UriComponentsBuilder.fromUriString(properties.frontendCallbackUrl())
                .queryParam("ticket", ticket)
                .queryParam("intent", intent.value())
                .build(true)
                .toUriString();
    }

    public String failureUrl(GoogleAuthIntent intent, GoogleAuthErrorCode errorCode) {
        GoogleAuthIntent resolved = intent == null ? GoogleAuthIntent.LOGIN : intent;
        return UriComponentsBuilder.fromUriString(properties.frontendCallbackUrl())
                .queryParam("error", errorCode.value())
                .queryParam("intent", resolved.value())
                .build(true)
                .toUriString();
    }
}
