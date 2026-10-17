package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.DemoAccountEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.RoleEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.DemoAccountJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DemoAccountRegistration {

    private static final String MERCHANT_ROLE = "MERCHANT";

    private final DemoAccountJpaRepository demoAccountRepository;
    private final MerchantJpaRepository merchantRepository;
    private final RoleJpaRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSubjectMapper mapper;
    private final DemoPackageAssignment demoPackageAssignment;

    @AuthTransactional
    public AuthSubject save(String username, String password, String passwordConfirm) {
        String normalizedUsername = DemoAccountSupport.normalizeUsername(username);
        if (password == null || password.length() < 8) {
            throw new BadRequestException("Sifre en az 8 karakter olmalidir");
        }
        if (passwordConfirm == null || !password.equals(passwordConfirm)) {
            throw new BadRequestException("Sifreler eslesmiyor");
        }
        if (demoAccountRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new BadRequestException("Bu kullanici adi zaten alinmis");
        }

        String email = DemoAccountSupport.demoEmailForUsername(normalizedUsername);
        if (merchantRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new BadRequestException("Bu kullanici adi zaten alinmis");
        }

        RoleEntity role = roleRepository.findByCode(MERCHANT_ROLE)
                .orElseThrow(() -> new IllegalStateException("MERCHANT role missing"));

        MerchantEntity merchant = new MerchantEntity();
        merchant.setRole(role);
        merchant.setEmail(email);
        merchant.setPassword(null);
        merchant.setFirstName(normalizedUsername);
        merchant.setLastName("Demo");
        merchant.setProvider("BASIC");
        merchant.setActive(true);
        merchant.setEmailVerified(true);
        merchantRepository.save(merchant);

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(DemoAccountSupport.DEMO_PACKAGE_VALIDITY_DAYS);
        DemoAccountEntity demoAccount = new DemoAccountEntity();
        demoAccount.setUsername(normalizedUsername);
        demoAccount.setPassword(passwordEncoder.encode(password));
        demoAccount.setMerchantId(merchant.getId());
        demoAccount.setTrialPackageCode(DemoAccountSupport.DEMO_PACKAGE_CODE);
        demoAccount.setExpiresAt(expiresAt);
        try {
            demoAccountRepository.save(demoAccount);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("Bu kullanici adi zaten alinmis");
        }

        demoPackageAssignment.assignIfAbsent(merchant.getId());

        return mapper.fromMerchant(merchant);
    }
}
