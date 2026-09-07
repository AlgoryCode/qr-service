package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.service.entitlement.PurchaseSelectionPolicy;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionAccessService {

    private final UserRepository userRepository;
    private final TrialLogRepository trialLogRepository;
    private final PurchaseRepository purchaseRepository;
    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final PurchaseSelectionPolicy purchaseSelectionPolicy;
    private final SessionAccessPolicy sessionAccessPolicy;

    @Transactional
    public AccessSession resolve(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return AccessSession.of(
                    com.ael.algoryqrservice.model.enums.AccessDecision.REQUIRE_PURCHASE,
                    null,
                    null,
                    null
            );
        }
        LocalDateTime now = AppTime.nowLocal();
        Optional<TrialLog> trialLog = expireDue(trialLogRepository.findByUserId(userId), now);
        Optional<Purchase> paid = governingPaid(userId);
        return sessionAccessPolicy.decide(user.getCreatedAt(), trialLog, paid, now);
    }

    @Transactional(readOnly = true)
    public Optional<Purchase> governingPaid(Long userId) {
        return purchaseSelectionPolicy.highestPriority(
                purchaseRepository.findByUserIdAndStatusAndPurchaseType(userId, PurchaseStatus.ACTIVE, PurchaseType.PAID)
                        .stream()
                        .filter(purchase -> !purchase.isExpiredByDate() || sessionAccessPolicy.isInDebt(purchase))
                        .toList()
        );
    }

    @Transactional
    public Optional<TrialLog> expireDue(Optional<TrialLog> trialLog, LocalDateTime now) {
        if (trialLog.isEmpty()) {
            return Optional.empty();
        }
        TrialLog log = trialLog.get();
        if (log.getStatus() == TrialLogStatus.ACTIVE && !log.isActiveAt(now)) {
            log.setStatus(TrialLogStatus.ENDED);
            trialLogRepository.save(log);
            grantFulfillmentRepository.findByTrialLogId(log.getId()).ifPresent(fulfillment -> {
                if (fulfillment.getStatus() == GrantFulfillmentStatus.ACTIVE) {
                    fulfillment.setStatus(GrantFulfillmentStatus.EXPIRED);
                    grantFulfillmentRepository.save(fulfillment);
                }
            });
        }
        return Optional.of(log);
    }
}
