package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.DemoAccountEntity;
import com.ael.algoryqrservice.merchantservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.DemoAccountJpaRepository;
import com.ael.algoryqrservice.merchantservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.security.LoginAttemptGuard;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DemoLoginUseCase {

    private final DemoAccountJpaRepository demoAccountRepository;
    private final MerchantJpaRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptGuard loginAttemptGuard;
    private final DemoPackageAssignment demoPackageAssignment;
    private final MerchantPackageGate merchantPackageGate;
    private final AuthSubjectMapper mapper;
    private final TokenUseCase tokenUseCase;

    public Map<String, Object> login(String username, String password, ClientInfo clientInfo) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BadRequestException("Kullanici adi ve sifre zorunludur");
        }
        String normalized = DemoAccountSupport.normalizeUsername(username);
        loginAttemptGuard.assertNotBlocked(normalized);

        DemoAccountEntity demo = demoAccountRepository.findByUsernameIgnoreCase(normalized)
                .orElseThrow(() -> {
                    loginAttemptGuard.registerFailure(normalized);
                    return new BadCredentialsException("Invalid credentials");
                });
        MerchantEntity merchant = merchantRepository.findActiveByIdWithRole(demo.getMerchantId())
                .orElseThrow(() -> {
                    loginAttemptGuard.registerFailure(normalized);
                    return new BadCredentialsException("Invalid credentials");
                });
        if (demo.getPassword() == null || !passwordEncoder.matches(password, demo.getPassword())) {
            loginAttemptGuard.registerFailure(normalized);
            throw new BadCredentialsException("Invalid credentials");
        }

        loginAttemptGuard.reset(normalized);
        demoPackageAssignment.assignIfAbsent(merchant.getId());
        AuthSubject subject = mapper.fromMerchant(merchant);
        merchantPackageGate.assertOpen(subject);
        return tokenUseCase.create(subject, clientInfo);
    }
}
