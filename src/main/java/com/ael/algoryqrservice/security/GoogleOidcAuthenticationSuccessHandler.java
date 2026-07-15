package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.GoogleOidcIdentity;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.service.GoogleAuthHandoffService;
import com.ael.algoryqrservice.service.GoogleAuthRedirectService;
import com.ael.algoryqrservice.service.GoogleAuthSessionService;
import com.ael.algoryqrservice.service.GoogleOAuthUserService;
import com.ael.algoryqrservice.util.ClientInfo;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOidcAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final GoogleAuthSessionService authSessionService;
    private final GoogleOAuthUserService googleOAuthUserService;
    private final GoogleAuthHandoffService handoffService;
    private final GoogleAuthRedirectService redirectService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            GoogleAuthIntent intent = authSessionService.requireIntent(request);
            GoogleOidcIdentity identity = identity(authentication);
            User user = googleOAuthUserService.resolve(intent, identity, ClientInfo.from(request));
            String ticket = handoffService.issue(user.getId(), intent);
            String redirectUrl = redirectService.success(ticket, intent);
            authSessionService.clear(request);
            response.sendRedirect(redirectUrl);
        } catch (RuntimeException exception) {
            log.warn("Google OAuth başarı akışı tamamlanamadı: {}", exception.getMessage(), exception);
            authSessionService.clear(request);
            response.sendRedirect(redirectService.failure());
        }
    }

    private GoogleOidcIdentity identity(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !(token.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new IllegalStateException("OIDC kullanıcısı bulunamadı");
        }
        return new GoogleOidcIdentity(
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                oidcUser.getGivenName(),
                oidcUser.getFamilyName(),
                Boolean.TRUE.equals(oidcUser.getEmailVerified())
        );
    }
}
