package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.GoogleOidcIdentity;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleIdTokenAuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final GoogleOAuthUserService googleOAuthUserService;
    private final SessionService sessionService;

    @Transactional
    public AuthResponse authenticate(String idToken, GoogleAuthIntent intent, ClientInfo clientInfo) {
        if (intent == null || intent.isCustomer()) {
            throw new BadRequestException("Google kimlik doğrulama amacı geçersiz");
        }
        GoogleOidcIdentity identity = googleIdTokenVerifier.verify(idToken);
        User user = googleOAuthUserService.resolve(intent, identity, clientInfo);
        SessionService.SessionTokens tokens = sessionService.createSession(user, clientInfo);
        return sessionService.buildAuthResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
