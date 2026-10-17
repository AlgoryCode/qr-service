package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleLoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginUseCase.class);

    private final GoogleIdTokenVerifier idTokenVerifier;
    private final GoogleIdentityUseCase identityUseCase;
    private final TokenUseCase tokenUseCase;
    private final MerchantPackageGate merchantPackageGate;

    @AuthTransactional
    public Map<String, Object> authenticate(String idToken, String intent, String audience, ClientInfo clientInfo) {
        GoogleAuthIntent parsed = GoogleAuthIntent.from(intent);
        GoogleOidcIdentity identity = idTokenVerifier.verify(idToken);
        AuthSubject subject = identityUseCase.resolve(
                parsed,
                audience,
                identity,
                clientInfo,
                GoogleProviders.MOBILE_GOOGLE
        );
        merchantPackageGate.assertOpen(subject);
        log.info(
                "Google id-token login succeeded subjectType={} subjectId={} intent={} clientIp={}",
                subject.subjectType(),
                subject.subjectId(),
                parsed.value(),
                clientInfo.ipAddress()
        );
        return tokenUseCase.create(subject, clientInfo);
    }
}
