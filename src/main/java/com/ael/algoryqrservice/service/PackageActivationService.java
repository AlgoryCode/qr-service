package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
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

    @Transactional
    public Purchase ensureFreePackage(Long userId) {
        List<Purchase> active = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE);
        if (!active.isEmpty()) {
            return selectHighestPackage(active);
        }

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
                .status(PurchaseStatus.ACTIVE)
                .startsAt(startsAt)
                .expiresAt(expiresAt)
                .build());

        for (PlanPackageItem item : freePackage.getItems()) {
            entitlementService.grant(
                    purchase,
                    item.getProduct().getId(),
                    item.getProduct().getCode(),
                    item.getQuantity(),
                    item.isUnlimited()
            );
        }
        return purchase;
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
    }

    @Transactional
    public void restoreFreePackagesAfterPaidExpiry() {
        List<Long> userIds = purchaseRepository.findDistinctUserIdsWithExpiredPaidPurchases(PurchaseStatus.EXPIRED);
        for (Long userId : userIds) {
            if (!purchaseRepository.existsByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)) {
                ensureFreePackage(userId);
            }
        }
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
                    return CatalogPackages.FREE_PACKAGE.equals(purchase.getPackageCode()) ? 1 : 0;
                }))
                .orElse(purchases.getFirst());
    }
}
