package com.ael.algoryqrservice.trial;

import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.trial.domain.TrialSnapshot;
import com.ael.algoryqrservice.trial.domain.TrialSnapshotFactory;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TrialSnapshotQuery {

    private final TrialLogRepository trialLogRepository;
    private final PurchaseRepository purchaseRepository;

    public TrialSnapshot forUser(Long userId) {
        TrialLog log = trialLogRepository.findByUserId(userId).orElse(null);
        boolean blocked = hasUsablePaidOrGrant(userId);
        LocalDateTime now = AppTime.nowLocal();
        if (log == null) {
            return TrialSnapshotFactory.from(null, null, false, false, blocked);
        }
        return TrialSnapshotFactory.from(
                log.getId(),
                log.getEndsAt(),
                log.isActiveAt(now),
                true,
                blocked
        );
    }

    private boolean hasUsablePaidOrGrant(Long userId) {
        return purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .anyMatch(purchase -> purchase.isUsable() && isBlockingPurchaseType(purchase.getPurchaseType()));
    }

    private static boolean isBlockingPurchaseType(PurchaseType type) {
        return type == PurchaseType.PAID || type == PurchaseType.SYSTEM_GRANT;
    }
}
