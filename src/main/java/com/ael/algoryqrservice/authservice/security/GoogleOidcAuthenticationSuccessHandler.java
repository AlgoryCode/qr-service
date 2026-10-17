package com.ael.algoryqrservice.authservice.security;

import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthErrorCode;
import com.ael.algoryqrservice.authservice.service.GoogleAuthRedirectService;
import com.ael.algoryqrservice.authservice.service.GoogleAuthSessionService;
import com.ael.algoryqrservice.authservice.application.auth.GoogleIdentityUseCase;
import com.ael.algoryqrservice.authservice.application.auth.GoogleOidcIdentity;
import com.ael.algoryqrservice.authservice.application.auth.GoogleProviders;
import com.ael.algoryqrservice.authservice.service.GoogleAuthHandoffService;
import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GoogleOidcAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOidcAuthenticationSuccessHandler.class);

    private final GoogleAuthSessionService sessionService;
    private final GoogleIdentityUseCase identityUseCase;
    private final GoogleAuthHandoffService handoffService;
    private final GoogleAuthRedirectService redirectService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        GoogleAuthSessionService.PendingGoogleAuth pending = sessionService.find(request).orElse(null);
        sessionService.clear(request, response);
        if (pending == null) {
            log.warn("Google OAuth callback without pending intent (session and cookie missing)");
            redirectService.failure(response, null, GoogleAuthErrorCode.INVALID_INTENT);
            return;
        }
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            redirectService.failure(response, pending.intent(), GoogleAuthErrorCode.GOOGLE_AUTH_FAILED);
            return;
        }
        complete(response, pending, oidcUser, ClientInfo.from(request));
    }

    private void complete(
            HttpServletResponse response,
            GoogleAuthSessionService.PendingGoogleAuth pending,
            OidcUser oidcUser,
            ClientInfo clientInfo
    ) throws IOException {
        try {
            AuthSubject subject = identityUseCase.resolve(
                    pending.intent(),
                    pending.audience(),
                    GoogleOidcIdentity.from(oidcUser),
                    clientInfo,
                    GoogleProviders.GOOGLE
            );
            String ticket = handoffService.issue(subject, pending.intent(), pending.audience());
            log.info(
                    "Google OAuth succeeded subjectType={} subjectId={} intent={}",
                    subject.subjectType(),
                    subject.subjectId(),
                    pending.intent().value()
            );
            redirectService.success(response, pending.intent(), ticket);
        } catch (BadRequestException | UnauthorizedException exception) {
            log.warn(
                    "Google OAuth business rule rejected intent={} audience={}: {}",
                    pending.intent().value(),
                    pending.audience(),
                    exception.getMessage()
            );
            redirectService.failure(response, pending.intent(), GoogleAuthErrorCode.fromMessage(exception.getMessage()));
        } catch (RuntimeException exception) {
            log.error("Google OAuth success flow failed", exception);
            redirectService.failure(response, pending.intent(), GoogleAuthErrorCode.GOOGLE_AUTH_FAILED);
        }
    }
}
