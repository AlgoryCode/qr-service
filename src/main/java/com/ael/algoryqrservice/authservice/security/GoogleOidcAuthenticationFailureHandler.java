package com.ael.algoryqrservice.authservice.security;

import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthErrorCode;
import com.ael.algoryqrservice.authservice.service.GoogleAuthRedirectService;
import com.ael.algoryqrservice.authservice.service.GoogleAuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GoogleOidcAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOidcAuthenticationFailureHandler.class);

    private final GoogleAuthSessionService sessionService;
    private final GoogleAuthRedirectService redirectService;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        GoogleAuthSessionService.PendingGoogleAuth pending = sessionService.find(request).orElse(null);
        sessionService.clear(request, response);

        GoogleAuthErrorCode errorCode = resolveErrorCode(exception);
        if (errorCode == GoogleAuthErrorCode.INVALID_CLIENT) {
            log.error(
                    "Google OAuth authentication failed intent={} errorCode={}: GOOGLE_CLIENT_SECRET does not match GOOGLE_CLIENT_ID. "
                            + "Copy the client secret from Google Cloud Console (APIs & Services > Credentials > OAuth 2.0 Client IDs).",
                    pending == null ? "unknown" : pending.intent().value(),
                    errorCode.value()
            );
        } else {
            log.warn(
                    "Google OAuth authentication failed intent={} errorCode={} reason={}",
                    pending == null ? "unknown" : pending.intent().value(),
                    errorCode.value(),
                    exception.getMessage(),
                    exception
            );
        }

        redirectService.failure(
                response,
                pending == null ? null : pending.intent(),
                errorCode
        );
    }

    private GoogleAuthErrorCode resolveErrorCode(AuthenticationException exception) {
        String aggregated = aggregateMessages(exception).toLowerCase();
        if (aggregated.contains("invalid_client") || aggregated.contains("client secret is invalid")) {
            return GoogleAuthErrorCode.INVALID_CLIENT;
        }
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            OAuth2Error error = oauthException.getError();
            if (error != null) {
                String code = error.getErrorCode() == null ? "" : error.getErrorCode().toLowerCase();
                if (code.contains("access_denied")) {
                    return GoogleAuthErrorCode.ACCESS_DENIED;
                }
            }
        }
        if (aggregated.contains("authorization request not found") || aggregated.contains("invalid state")) {
            return GoogleAuthErrorCode.INVALID_INTENT;
        }
        return GoogleAuthErrorCode.OAUTH_FAILED;
    }

    private static String aggregateMessages(Throwable exception) {
        StringBuilder builder = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString();
    }
}
