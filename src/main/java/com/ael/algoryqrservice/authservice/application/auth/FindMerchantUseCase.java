package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.NotFoundException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FindMerchantUseCase {

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    private final MerchantJpaRepository merchantRepository;

    @AuthTransactional(readOnly = true)
    public MerchantResponse findMerchant(Long merchantId) {
        MerchantEntity merchant = merchantRepository.findWithRoleById(merchantId)
                .orElseThrow(() -> new NotFoundException("Merchant bulunamadi"));
        String status = merchant.isActive() && merchant.getDeletedAt() == null ? ACTIVE : INACTIVE;
        return new MerchantResponse(merchant.getId(), status);
    }

    public Optional<Long> findActiveMerchantIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return merchantRepository.findByEmailIgnoreCaseAndActiveTrueAndDeletedAtIsNull(email.trim())
                .map(MerchantEntity::getId);
    }

    public record MerchantResponse(Long id, String status) {
    }
}
