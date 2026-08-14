package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageActivationService {

    private final PlanPackageRepository planPackageRepository;
    private final PurchaseRepository purchaseRepository;
    private final EntitlementService entitlementService;
    private final MenuPublicAccessService menuPublicAccessService;

    @Transactional
    public Optional<Purchase> ensureSubscriptionState(Long userId) {
        entitlementService.expireDuePurchasesForUser(userId);
        List<Purchase> usablePaidOrTrial = findUsablePaidOrTrial(userId);
        menuPublicAccessService.syncForUser(userId);
        if (usablePaidOrTrial.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(selectHighestPackage(usablePaidOrTrial));
    }

    @Transactional
    public void syncSubscriptionStateForUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userIds.stream().distinct().forEach(this::ensureSubscriptionState);
    }

    @Transactional
    public void activatePurchasedPackage(Purchase purchasedPackage) {
        List<Purchase> active = purchaseRepository.findByUserIdAndStatus(
                purchasedPackage.getUserId(),
                PurchaseStatus.ACTIVE
        );
        for (Purchase purchase : active) {
            if (!purchase.getId().equals(purchasedPackage.getId())) {
                purchase.setStatus(PurchaseStatus.SUPERSEDED);
                purchaseRepository.save(purchase);
            }
        }
        menuPublicAccessService.syncForUser(purchasedPackage.getUserId());
    }

    @Transactional
    public void syncAfterPaidExpiry() {
        List<Long> userIds = purchaseRepository.findDistinctUserIdsWithExpiredPaidPurchases(PurchaseStatus.EXPIRED);
        syncSubscriptionStateForUsers(userIds);
    }

    private List<Purchase> findUsablePaidOrTrial(Long userId) {
        return purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .filter(this::isPaidOrTrialPurchase)
                .toList();
    }

    private boolean isPaidOrTrialPurchase(Purchase purchase) {
        if (purchase.getPurchaseType() == PurchaseType.FREE) {
            return false;
        }
        if (CatalogPackages.FREE_PACKAGE.equals(purchase.getPackageCode())) {
            return false;
        }
        return purchase.getPurchaseType() == PurchaseType.PAID
                || purchase.getPurchaseType() == PurchaseType.TRIAL
                || purchase.getPurchaseType() == PurchaseType.SYSTEM_GRANT;
    }

    private Purchase selectHighestPackage(List<Purchase> purchases) {
        Map<Long, PlanPackage> packagesById = planPackageRepository.findAllById(
                purchases.stream().map(Purchase::getPackageId).distinct().toList()
        ).stream().collect(Collectors.toMap(PlanPackage::getId, Function.identity()));

        return purchases.stream()
                .max(Comparator.comparingInt(purchase -> {
                    PlanPackage planPackage = packagesById.get(purchase.getPackageId());
                    if (planPackage != null && planPackage.getPriority() != null) {
                        return planPackage.getPriority();
                    }
                    return 0;
                }))
                .orElse(purchases.getFirst());
    }
}
