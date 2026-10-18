package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.AuthErrorCodes;
import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.exception.ForbiddenException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.DemoAccountJpaRepository;
import com.ael.algoryqrservice.merchantservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.security.LoginAttemptGuard;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

    private final IdentifierResolver identifierResolver;
    private final PasswordEncoder passwordEncoder;
    private final TokenUseCase tokenUseCase;
    private final MerchantPackageGate merchantPackageGate;
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

        AuthSubject subject = identifierResolver.resolve(identifier, merchantId)
                .orElseThrow(() -> {
                    loginAttemptGuard.registerFailure(key);
                    log.warn("Login failed: subject not found identifier={} clientIp={}", key, clientInfo.ipAddress());
                    return new BadCredentialsException("Invalid credentials");
                });

        if (GoogleProviders.isGoogle(subject.provider())) {
            throw new BadRequestException("This account uses Google sign-in");
        }
        if (subject.passwordHash() == null || !passwordEncoder.matches(password, subject.passwordHash())) {
            loginAttemptGuard.registerFailure(key);
            log.warn(
                    "Login failed: invalid password subjectType={} subjectId={} clientIp={}",
                    subject.subjectType(),
                    subject.subjectId(),
                    clientInfo.ipAddress()
            );
            throw new BadCredentialsException("Invalid credentials");
        }

        loginAttemptGuard.reset(key);
        if (AuthSubject.TYPE_MERCHANT.equals(subject.subjectType())
                && demoAccountRepository.findByMerchantId(subject.subjectId()).isPresent()) {
            throw new BadRequestException("Demo girisi ayri ekrandan yapilir");
        }
        requireMerchantEmailVerified(subject);
        merchantPackageGate.assertOpen(subject);
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

        AuthSubject subject = identifierResolver.resolveStaff(merchantId, username)
                .filter(found -> AuthSubject.TYPE_STAFF.equals(found.subjectType()))
                .orElseThrow(() -> {
                    loginAttemptGuard.registerFailure(key);
                    return new BadCredentialsException("Invalid credentials");
                });

        if (subject.passwordHash() == null || !passwordEncoder.matches(password, subject.passwordHash())) {
            loginAttemptGuard.registerFailure(key);
            throw new BadCredentialsException("Invalid credentials");
        }

        loginAttemptGuard.reset(key);
        if (subject.branchId() == null) {
            throw new BadRequestException("Personel bir şubeye atanmamış");
        }
        merchantPackageGate.assertOpen(subject);
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
