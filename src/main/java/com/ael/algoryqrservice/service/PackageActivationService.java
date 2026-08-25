package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.service.entitlement.PurchaseSelectionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PackageActivationService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseExpiryService purchaseExpiryService;
    private final PurchaseSelectionPolicy purchaseSelectionPolicy;
    private final MenuPublicAccessService menuPublicAccessService;

    @Transactional
    public Optional<Purchase> ensureSubscriptionState(Long userId) {
        purchaseExpiryService.expireDueForUser(userId);
        List<Purchase> subscriptions = purchaseSelectionPolicy.usableSubscriptions(userId);
        menuPublicAccessService.syncForUser(userId);
        return purchaseSelectionPolicy.highestPriority(subscriptions);
    }

    @Transactional
    public void syncSubscriptionStateForUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userIds.stream().distinct().forEach(this::ensureSubscriptionState);
    }

    /**
     * Marks a newly paid subscription as the only active one; add-ons stack instead of superseding.
     */
    @Transactional
    public void activatePurchasedPackage(Purchase purchasedPackage) {
        if (purchasedPackage.getPurchaseType() != PurchaseType.ADD_ON) {
            supersedePreviousSubscriptions(purchasedPackage);
        }
        menuPublicAccessService.syncForUser(purchasedPackage.getUserId());
    }

    @Transactional
    public void syncAfterPaidExpiry() {
        List<Long> userIds = purchaseRepository.findDistinctUserIdsWithExpiredPaidPurchases(PurchaseStatus.EXPIRED);
        syncSubscriptionStateForUsers(userIds);
    }

    private void supersedePreviousSubscriptions(Purchase activated) {
        List<Purchase> superseded = purchaseRepository
                .findByUserIdAndStatus(activated.getUserId(), PurchaseStatus.ACTIVE).stream()
                .filter(purchase -> !purchase.getId().equals(activated.getId()))
                .filter(purchase -> purchase.getPurchaseType() != PurchaseType.ADD_ON)
                .toList();
        superseded.forEach(purchase -> purchase.setStatus(PurchaseStatus.SUPERSEDED));
        purchaseRepository.saveAll(superseded);
    }
}
