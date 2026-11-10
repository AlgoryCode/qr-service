package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.AuthErrorCodes;
import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.exception.ForbiddenException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.DemoAccountJpaRepository;
import com.ael.algoryqrservice.merchantservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.security.LoginAttemptGuard;
import com.ael.algoryqrservice.authservice.security.PasswordVerifier;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

    private final IdentifierResolver identifierResolver;
    private final PasswordVerifier passwordVerifier;
    private final TokenUseCase tokenUseCase;
    private final LoginAttemptGuard loginAttemptGuard;
    private final MerchantJpaRepository merchantRepository;
    private final DemoAccountJpaRepository demoAccountRepository;

    public Map<String, Object> login(String identifier, String password, ClientInfo clientInfo) {
        return login(identifier, password, null, clientInfo);
    }

    public Map<String, Object> login(String identifier, String password, Long merchantId, ClientInfo clientInfo) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            throw new BadRequestException("identifier and password are required");
        }
        String key = identifier.trim().toLowerCase();
        loginAttemptGuard.assertNotBlocked(key);

        // Same work and same answer whether the account is missing, Google-only or has a wrong password,
        // so neither the message nor the response time reveals which e-mails are registered.
        Optional<AuthSubject> found = identifierResolver.resolve(identifier, merchantId);
        boolean passwordOk = passwordVerifier.matches(password, found.map(AuthSubject::passwordHash).orElse(null));
        if (found.isEmpty() || !passwordOk) {
            loginAttemptGuard.registerFailure(key);
            log.warn(
                    "Login failed: {} identifier={} clientIp={}",
                    found.isEmpty() ? "subject not found" : "invalid password",
                    key,
                    clientInfo.ipAddress()
            );
            throw new BadCredentialsException("Invalid credentials");
        }
        AuthSubject subject = found.get();
        if (GoogleProviders.isGoogle(subject.provider())) {
            throw new BadRequestException("This account uses Google sign-in");
        }

        loginAttemptGuard.reset(key);
        if (AuthSubject.TYPE_MERCHANT.equals(subject.subjectType())
                && demoAccountRepository.findByMerchantId(subject.subjectId()).isPresent()) {
            throw new BadRequestException("Demo girisi ayri ekrandan yapilir");
        }
        requireMerchantEmailVerified(subject);
        log.info(
                "Login succeeded subjectType={} subjectId={} clientIp={}",
                subject.subjectType(),
                subject.subjectId(),
                clientInfo.ipAddress()
        );
        return tokenUseCase.create(subject, clientInfo);
    }

    public Map<String, Object> loginStaff(String username, String password, Long merchantId, ClientInfo clientInfo) {
        if (merchantId == null || merchantId <= 0) {
            throw new BadRequestException("merchantId is required");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BadRequestException("username and password are required");
        }
        String key = "staff:" + merchantId + ":" + username.trim().toLowerCase();
        loginAttemptGuard.assertNotBlocked(key);

        Optional<AuthSubject> found = identifierResolver.resolveStaff(merchantId, username)
                .filter(candidate -> AuthSubject.TYPE_STAFF.equals(candidate.subjectType()));
        if (!passwordVerifier.matches(password, found.map(AuthSubject::passwordHash).orElse(null))) {
            loginAttemptGuard.registerFailure(key);
            throw new BadCredentialsException("Invalid credentials");
        }
        AuthSubject subject = found.get();

        loginAttemptGuard.reset(key);
        if (subject.branchId() == null) {
            throw new BadRequestException("Personel bir şubeye atanmamış");
        }
        log.info(
                "Staff login succeeded merchantId={} subjectId={} clientIp={}",
                merchantId,
                subject.subjectId(),
                clientInfo.ipAddress()
        );
        return tokenUseCase.create(subject, clientInfo);
    }

    private void requireMerchantEmailVerified(AuthSubject subject) {
        if (!AuthSubject.TYPE_MERCHANT.equals(subject.subjectType())) {
            return;
        }
        merchantRepository.findById(subject.subjectId()).ifPresent(merchant -> {
            if ("BASIC".equals(merchant.getProvider()) && !merchant.isEmailVerified()) {
                throw new ForbiddenException(
                        AuthErrorCodes.EMAIL_NOT_VERIFIED,
                        AuthErrorCodes.EMAIL_NOT_VERIFIED_MESSAGE
                );
            }
        });
    }
}
