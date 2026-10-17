package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.RoleEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.RoleJpaRepository;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegisterMerchantUseCase {

    private static final String MERCHANT_ROLE = "MERCHANT";

    private final MerchantJpaRepository merchantRepository;
    private final RoleJpaRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenUseCase tokenUseCase;
    private final AuthSubjectMapper mapper;
    private final EmailVerificationUseCase emailVerificationUseCase;

    @AuthTransactional
    public Map<String, Object> register(
            String email,
            String password,
            String firstName,
            String lastName,
            String phone,
            ClientInfo clientInfo
    ) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new BadRequestException("Valid email is required");
        }
        if (password == null || password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }
        if (firstName == null || firstName.isBlank()) {
            throw new BadRequestException("firstName is required");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (merchantRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)) {
            throw new BadRequestException("Email already registered");
        }

        RoleEntity role = roleRepository.findByCode(MERCHANT_ROLE)
                .orElseThrow(() -> new IllegalStateException("MERCHANT role missing"));

        MerchantEntity merchant = new MerchantEntity();
        merchant.setRole(role);
        merchant.setEmail(normalizedEmail);
        merchant.setPassword(passwordEncoder.encode(password));
        merchant.setFirstName(firstName.trim());
        merchant.setLastName(lastName == null ? null : lastName.trim());
        merchant.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        merchant.setProvider("BASIC");
        merchant.setActive(true);
        merchant.setEmailVerified(false);
        merchantRepository.save(merchant);
        emailVerificationUseCase.issue(merchant);

        AuthSubject subject = mapper.fromMerchant(merchant);
        return tokenUseCase.create(subject, clientInfo);
    }
}
