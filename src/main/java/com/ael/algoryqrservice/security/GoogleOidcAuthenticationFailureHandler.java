package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.model.enums.GoogleAuthErrorCode;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.service.GoogleAuthRedirectService;
import com.ael.algoryqrservice.service.GoogleAuthSessionService;
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
        GoogleAuthIntent intent = sessionService.findIntent(request).orElse(null);
        sessionService.clear(request);

        GoogleAuthErrorCode errorCode = resolveErrorCode(exception);
        log.warn(
                "Google OAuth kimlik doğrulaması başarısız: intent={} code={} detail={}",
                intent,
                errorCode.value(),
                describe(exception)
        );
        redirectService.failure(response, intent, errorCode);
    }

    private GoogleAuthErrorCode resolveErrorCode(AuthenticationException exception) {
        if (!(exception instanceof OAuth2AuthenticationException oauthException)) {
            return GoogleAuthErrorCode.GOOGLE_AUTH_FAILED;
        }
        OAuth2Error error = oauthException.getError();
        if (error == null || error.getErrorCode() == null) {
            return GoogleAuthErrorCode.GOOGLE_AUTH_FAILED;
        }
        if ("access_denied".equalsIgnoreCase(error.getErrorCode())) {
            return GoogleAuthErrorCode.ACCESS_DENIED;
        }
        return GoogleAuthErrorCode.GOOGLE_AUTH_FAILED;
    }

    private String describe(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            OAuth2Error error = oauthException.getError();
            if (error != null) {
                return error.getErrorCode()
                        + (error.getDescription() == null || error.getDescription().isBlank()
                        ? ""
                        : " - " + error.getDescription());
            }
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
