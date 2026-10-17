package com.ael.algoryqrservice.qrservice.billing.service.entitlement;

import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Single definition of "which purchase currently governs this user".
 * Add-ons, free plans and system-managed grants never win that role.
 */
@Component
@RequiredArgsConstructor
public class PurchaseSelectionPolicy {

    private static final int NO_PRIORITY = 0;

    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository planPackageRepository;

    /**
     * @return every purchase that is active today, regardless of type.
     */
    @Transactional(readOnly = true)
    public List<Purchase> usablePurchases(Long merchantId) {
        if (merchantId == null) {
            return List.of();
        }
        return purchaseRepository.findByMerchantIdAndStatus(merchantId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .toList();
    }

    /**
     * @return active subscription-like purchases: paid or granted, but never add-ons or free plans.
     */
    @Transactional(readOnly = true)
    public List<Purchase> usableSubscriptions(Long merchantId) {
        return usablePurchases(merchantId).stream()
                .filter(this::isSubscriptionLike)
                .toList();
    }

    /**
     * @return active add-on purchases.
     */
    @Transactional(readOnly = true)
    public List<Purchase> usableAddons(Long merchantId) {
        return usablePurchases(merchantId).stream()
                .filter(purchase -> purchase.getPurchaseType() == PurchaseType.ADD_ON)
                .toList();
    }

    /**
     * @return id of the subscription that currently governs the user, or {@code null} when there is none.
     */
    @Transactional(readOnly = true)
    public Long activePurchaseId(Long merchantId) {
        return highestPriority(usableSubscriptions(merchantId)).map(Purchase::getId).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isActivePurchase(Long merchantId, Long purchaseId) {
        return purchaseId != null && Objects.equals(activePurchaseId(merchantId), purchaseId);
    }

    public boolean isSubscriptionLike(Purchase purchase) {
        if (purchase.isSystemManaged()) {
            return false;
        }
        PurchaseType type = purchase.getPurchaseType();
        return type != PurchaseType.FREE && type != PurchaseType.ADD_ON;
    }

    /**
     * @return the purchase whose plan package has the highest priority.
     */
    public Optional<Purchase> highestPriority(List<Purchase> purchases) {
        if (purchases.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Integer> priorityByPackageId = priorityByPackageId(purchases);
        return purchases.stream().max(Comparator.comparingInt(
                purchase -> priorityByPackageId.getOrDefault(purchase.getPackageId(), NO_PRIORITY)
        ));
    }

    private Map<Long, Integer> priorityByPackageId(List<Purchase> purchases) {
        List<Long> packageIds = purchases.stream()
                .map(Purchase::getPackageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return planPackageRepository.findAllById(packageIds).stream().collect(Collectors.toMap(
                PlanPackage::getId,
                planPackage -> planPackage.getPriority() == null ? NO_PRIORITY : planPackage.getPriority(),
                (left, right) -> left
        ));
    }
}
