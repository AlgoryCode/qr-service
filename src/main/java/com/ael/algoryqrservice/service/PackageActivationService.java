package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageActivationService {

    private final PackageCatalogService packageCatalogService;
    private final PlanPackageRepository planPackageRepository;
    private final PurchaseRepository purchaseRepository;
    private final EntitlementService entitlementService;
    private final MenuPublicAccessService menuPublicAccessService;

    @Transactional
    public Purchase ensureFreePackage(Long userId) {
        entitlementService.expireDuePurchasesForUser(userId);

        List<Purchase> usablePaidOrTrial = findUsableNonFree(userId);
        ensureBaselineFree(userId, !usablePaidOrTrial.isEmpty());

        if (!usablePaidOrTrial.isEmpty()) {
            return selectHighestPackage(usablePaidOrTrial);
        }

        Purchase freePurchase = activateBaselineFree(userId);
        menuPublicAccessService.syncForUser(userId);
        return freePurchase;
    }

    @Transactional
    public void ensureFreeForUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userIds.stream().distinct().forEach(this::ensureFreePackage);
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
        ensureBaselineFree(purchasedPackage.getUserId(), true);
        menuPublicAccessService.syncForUser(purchasedPackage.getUserId());
    }

    @Transactional
    public void restoreFreePackagesAfterPaidExpiry() {
        List<Long> userIds = purchaseRepository.findDistinctUserIdsWithExpiredPaidPurchases(PurchaseStatus.EXPIRED);
        ensureFreeForUsers(userIds);
    }

    private List<Purchase> findUsableNonFree(Long userId) {
        return purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .filter(purchase -> !isFreePurchase(purchase))
                .toList();
    }

    private void ensureBaselineFree(Long userId, boolean paidOrTrialActive) {
        Purchase freePurchase = findBaselineFree(userId).orElse(null);
        if (freePurchase == null) {
            if (paidOrTrialActive) {
                createBaselineFree(userId, PurchaseStatus.SUPERSEDED);
            }
            return;
        }
        if (paidOrTrialActive && freePurchase.getStatus() == PurchaseStatus.ACTIVE) {
            freePurchase.setStatus(PurchaseStatus.SUPERSEDED);
            purchaseRepository.save(freePurchase);
        }
    }

    private Purchase activateBaselineFree(Long userId) {
        PlanPackage freePackage = packageCatalogService.ensureFreePackage();
        Purchase freePurchase = findBaselineFree(userId).orElse(null);
        LocalDateTime startsAt = LocalDateTime.now();
        LocalDateTime expiresAt = startsAt.plusDays(freePackage.getValidityDays());

        if (freePurchase == null) {
            freePurchase = createBaselineFree(userId, PurchaseStatus.ACTIVE);
            return freePurchase;
        }

        freePurchase.setPackageId(freePackage.getId());
        freePurchase.setPackageCode(CatalogPackages.FREE_PACKAGE);
        freePurchase.setPackageName(freePackage.getName());
        freePurchase.setPrice(BigDecimal.ZERO);
        freePurchase.setCurrency(freePackage.getCurrency());
        freePurchase.setPurchaseType(PurchaseType.FREE);
        freePurchase.setPaymentStyle(PaymentStyle.ONE_TIME);
        freePurchase.setStatus(PurchaseStatus.ACTIVE);
        freePurchase.setStartsAt(startsAt);
        freePurchase.setExpiresAt(expiresAt);
        freePurchase.setCancellationReason(null);
        Purchase saved = purchaseRepository.save(freePurchase);
        entitlementService.refreshForPackage(saved, freePackage);
        return saved;
    }

    private Purchase createBaselineFree(Long userId, PurchaseStatus status) {
        PlanPackage freePackage = packageCatalogService.ensureFreePackage();
        LocalDateTime startsAt = LocalDateTime.now();
        LocalDateTime expiresAt = startsAt.plusDays(freePackage.getValidityDays());
        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .userId(userId)
                .packageId(freePackage.getId())
                .packageCode(CatalogPackages.FREE_PACKAGE)
                .packageName(freePackage.getName())
                .price(BigDecimal.ZERO)
                .currency(freePackage.getCurrency())
                .purchaseType(PurchaseType.FREE)
                .paymentStyle(PaymentStyle.ONE_TIME)
                .status(status)
                .startsAt(startsAt)
                .expiresAt(expiresAt)
                .build());
        entitlementService.refreshForPackage(purchase, freePackage);
        return purchase;
    }

    private java.util.Optional<Purchase> findBaselineFree(Long userId) {
        return purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(
                userId,
                PurchaseType.FREE
        );
    }

    private boolean isFreePurchase(Purchase purchase) {
        return purchase.getPurchaseType() == PurchaseType.FREE
                || CatalogPackages.FREE_PACKAGE.equals(purchase.getPackageCode());
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
                    return isFreePurchase(purchase) ? 1 : 0;
                }))
                .orElse(purchases.getFirst());
    }
}
