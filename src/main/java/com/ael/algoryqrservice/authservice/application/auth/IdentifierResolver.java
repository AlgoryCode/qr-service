package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.AdminJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.DemoAccountJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantStaffJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IdentifierResolver {

    private final MerchantStaffJpaRepository staffRepository;
    private final AdminJpaRepository adminRepository;
    private final MerchantJpaRepository merchantRepository;
    private final DemoAccountJpaRepository demoAccountRepository;
    private final AuthSubjectMapper mapper;
    private final FindMerchantUseCase findMerchantUseCase;

    @AuthTransactional(readOnly = true)
    public Optional<AuthSubject> resolve(String rawIdentifier) {
        return resolve(rawIdentifier, null);
    }

    @AuthTransactional(readOnly = true)
    public Optional<AuthSubject> resolve(String rawIdentifier, Long merchantId) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            return Optional.empty();
        }
        String identifier = rawIdentifier.trim();
        Optional<AuthSubject> scoped = resolveStaff(merchantId, identifier);
        if (scoped.isPresent()) {
            return scoped;
        }
        if (!identifier.contains("@")) {
            var demoMerchant = demoAccountRepository.findByUsernameIgnoreCase(identifier)
                    .flatMap(demo -> merchantRepository.findById(demo.getMerchantId()))
                    .filter(merchant -> FindMerchantUseCase.ACTIVE.equals(findMerchantUseCase.findMerchant(merchant.getId()).status()))
                    .map(mapper::fromMerchant);
            if (demoMerchant.isPresent()) {
                return demoMerchant;
            }
            return staffRepository.findByUsernameIgnoreCaseAndActiveTrue(identifier)
                    .map(mapper::fromStaff);
        }
        String email = identifier.toLowerCase();
        List<AuthSubject> matches = new ArrayList<>();
        adminRepository.findByEmailIgnoreCaseAndActiveTrue(email).map(mapper::fromAdmin).ifPresent(matches::add);
        merchantRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .filter(merchant -> FindMerchantUseCase.ACTIVE.equals(findMerchantUseCase.findMerchant(merchant.getId()).status()))
                .map(mapper::fromMerchant)
                .ifPresent(matches::add);
        if (matches.size() > 1) {
            throw new IllegalStateException("Multiple active identities for email");
        }
        return matches.stream().findFirst();
    }

    @AuthTransactional(readOnly = true)
    public Optional<AuthSubject> resolveStaff(Long merchantId, String rawIdentifier) {
        if (merchantId == null || merchantId <= 0 || rawIdentifier == null || rawIdentifier.isBlank()) {
            return Optional.empty();
        }
        return staffRepository
                .findByMerchantIdAndUsernameIgnoreCaseAndActiveTrue(merchantId, rawIdentifier.trim())
                .map(mapper::fromStaff);
    }
}
