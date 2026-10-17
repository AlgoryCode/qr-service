package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.exception.BadRequestException;

@Service
@RequiredArgsConstructor
public class GoogleIdentityUseCase {

    private final GoogleSubjectResolver resolver;

    public AuthSubject resolve(
            GoogleAuthIntent intent,
            String audience,
            GoogleOidcIdentity identity,
            ClientInfo clientInfo,
            String provider
    ) {
        if (intent == GoogleAuthIntent.LOGIN) {
            return resolver.login(audience, identity);
        }
        if (intent == GoogleAuthIntent.REGISTER) {
            return resolver.register(audience, identity, clientInfo, provider);
        }
        throw new com.ael.algoryqrservice.authservice.exception.BadRequestException("Google kimlik doğrulama amacı geçersiz");
    }
}
