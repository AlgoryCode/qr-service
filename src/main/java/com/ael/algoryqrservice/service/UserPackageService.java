package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPackageService {

    private final PackageCatalogService packageCatalogService;
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
                .packageCode(PackageCode.FREE_PACKAGE)
                .packageName(freePackage.getName())
                .price(BigDecimal.ZERO)
                .currency(freePackage.getCurrency())
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
    public void activateProPackage(Purchase proPurchase) {
        List<Purchase> active = purchaseRepository.findByUserIdAndStatus(
                proPurchase.getUserId(),
                PurchaseStatus.ACTIVE
        );
        for (Purchase purchase : active) {
            if (!purchase.getId().equals(proPurchase.getId())) {
                purchase.setStatus(PurchaseStatus.SUPERSEDED);
                purchaseRepository.save(purchase);
            }
        }
    }

    @Transactional
    public void restoreFreePackagesAfterProExpiry() {
        List<Long> userIds = purchaseRepository.findDistinctUserIdsByPackageCodeAndStatus(
                PackageCode.PRO_PACKAGE,
                PurchaseStatus.EXPIRED
        );
        for (Long userId : userIds) {
            if (!purchaseRepository.existsByUserIdAndStatus(userId, PurchaseStatus.ACTIVE)) {
                ensureFreePackage(userId);
            }
        }
    }

    private Purchase selectHighestPackage(List<Purchase> purchases) {
        return purchases.stream()
                .filter(purchase -> purchase.getPackageCode() == PackageCode.PRO_PACKAGE)
                .findFirst()
                .orElse(purchases.getFirst());
    }
}
