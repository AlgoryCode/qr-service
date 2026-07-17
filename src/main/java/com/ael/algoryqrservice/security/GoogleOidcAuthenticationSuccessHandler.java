package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.GoogleOidcIdentity;
import com.ael.algoryqrservice.model.enums.GoogleAuthErrorCode;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.service.GoogleAuthHandoffService;
import com.ael.algoryqrservice.service.GoogleAuthRedirectService;
import com.ael.algoryqrservice.service.GoogleAuthSessionService;
import com.ael.algoryqrservice.service.GoogleOAuthUserService;
import com.ael.algoryqrservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GoogleOidcAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOidcAuthenticationSuccessHandler.class);

    private final GoogleAuthSessionService sessionService;
    private final GoogleOAuthUserService googleOAuthUserService;
    private final GoogleAuthHandoffService handoffService;
    private final GoogleAuthRedirectService redirectService;

    public GoogleOidcAuthenticationSuccessHandler(
            GoogleAuthSessionService sessionService,
            GoogleOAuthUserService googleOAuthUserService,
            GoogleAuthHandoffService handoffService,
            GoogleAuthRedirectService redirectService
    ) {
        this.sessionService = sessionService;
        this.googleOAuthUserService = googleOAuthUserService;
        this.handoffService = handoffService;
        this.redirectService = redirectService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        GoogleAuthIntent intent = sessionService.findIntent(request).orElse(null);
        sessionService.clear(request);

        if (intent == null) {
            redirectService.failure(response, null, GoogleAuthErrorCode.INVALID_INTENT);
            return;
        }

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            redirectService.failure(response, intent, GoogleAuthErrorCode.GOOGLE_AUTH_FAILED);
            return;
        }

        try {
            GoogleOidcIdentity identity = GoogleOidcIdentity.from(oidcUser);
            ClientInfo clientInfo = ClientInfo.from(request);
            User user = googleOAuthUserService.resolve(intent, identity, clientInfo);
            String ticket = handoffService.issue(user.getId(), intent);
            redirectService.success(response, intent, ticket);
        } catch (BadRequestException | UnauthorizedException exception) {
            log.warn("Google OAuth başarı akışı tamamlanamadı: {}", exception.getMessage());
            redirectService.failure(response, intent, GoogleAuthErrorCode.fromMessage(exception.getMessage()));
        } catch (Exception exception) {
            log.error("Google OAuth başarı akışında beklenmeyen hata", exception);
            redirectService.failure(response, intent, GoogleAuthErrorCode.GOOGLE_AUTH_FAILED);
        }
    }
}
