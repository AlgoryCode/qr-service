package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.SessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SoftDeleteMerchantUseCase {

    private final MerchantJpaRepository merchantRepository;
    private final SessionJpaRepository sessionRepository;

    @AuthTransactional
    public void softDelete(Long merchantId) {
        MerchantEntity merchant = merchantRepository.findById(merchantId).orElse(null);
        if (merchant == null || merchant.getDeletedAt() != null) {
            return;
        }
        merchant.setDeletedAt(LocalDateTime.now());
        merchant.setActive(false);
        merchant.setPhone(null);
        merchant.setProviderSubject(null);
        merchantRepository.save(merchant);
        revokeSessions(merchantId);
    }

    private void revokeSessions(Long merchantId) {
        List<SessionEntity> sessions = sessionRepository.findBySubjectTypeAndSubjectIdAndRevokedFalse(
                AuthSubject.TYPE_MERCHANT,
                merchantId
        );
        LocalDateTime now = LocalDateTime.now();
        for (SessionEntity session : sessions) {
            session.setRevoked(true);
            session.setLastActivityAt(now);
        }
        sessionRepository.saveAll(sessions);
    }
}
