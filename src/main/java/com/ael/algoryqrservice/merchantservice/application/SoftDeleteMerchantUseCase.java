package com.ael.algoryqrservice.merchantservice.application;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.SessionJpaRepository;
import com.ael.algoryqrservice.merchantservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.merchantservice.infrastructure.persistence.repository.MerchantJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SoftDeleteMerchantUseCase {

    private final MerchantJpaRepository merchantRepository;
    private final SessionJpaRepository sessionRepository;

    @Transactional
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
        log.info("[merchant] Soft-deleted merchantId={}", merchantId);
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
