package com.ael.algoryqrservice.trial;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.trial.domain.TrialSnapshot;
import com.ael.algoryqrservice.trial.domain.TrialSnapshotFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TrialSnapshotQuery {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseExpiryService purchaseExpiryService;

    public TrialSnapshot forUser(Long userId) {
        purchaseExpiryService.expireDueForUser(userId);
        Optional<Purchase> trial = purchaseRepository
                .findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(userId, PurchaseType.TRIAL);
        boolean blocked = hasUsableNonTrialSubscription(userId);
        return trial
                .map(purchase -> TrialSnapshotFactory.from(
                        purchase.getId(),
                        purchase.getExpiresAt(),
                        purchase.isUsable(),
                        true,
                        blocked
                ))
                .orElseGet(() -> TrialSnapshotFactory.from(null, null, false, false, blocked));
    }

    private boolean hasUsableNonTrialSubscription(Long userId) {
        return purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .anyMatch(purchase -> purchase.isUsable() && isBlockingPurchaseType(purchase.getPurchaseType()));
    }

    private static boolean isBlockingPurchaseType(PurchaseType type) {
        return type == PurchaseType.PAID || type == PurchaseType.SYSTEM_GRANT;
    }
}
