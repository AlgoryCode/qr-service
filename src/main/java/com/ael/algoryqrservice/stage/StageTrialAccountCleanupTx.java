package com.ael.algoryqrservice.stage;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StageTrialAccountCleanupTx {

    private final TrialLogRepository trialLogRepository;
    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean isDue(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getDeletedAt() != null) {
            return false;
        }
        TrialLog trialLog = trialLogRepository.findByUserId(userId).orElse(null);
        if (trialLog == null || !trialEnded(trialLog)) {
            return false;
        }
        return purchaseRepository
                .findByUserIdAndStatusAndPurchaseType(userId, PurchaseStatus.ACTIVE, PurchaseType.PAID)
                .stream()
                .noneMatch(Purchase::isUsable);
    }

    @Transactional
    public void markDeleted(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getDeletedAt() != null) {
            return;
        }
        user.setDeletedAt(AppTime.nowLocal());
        user.setPhone(null);
        user.setProviderSubject(null);
        userRepository.save(user);
        log.info("Stage trial account soft-deleted. userId={}", userId);
    }

    private static boolean trialEnded(TrialLog trialLog) {
        LocalDateTime now = AppTime.nowLocal();
        return trialLog.getStatus() == TrialLogStatus.ENDED
                || trialLog.getEndsAt() == null
                || !trialLog.getEndsAt().isAfter(now);
    }
}
