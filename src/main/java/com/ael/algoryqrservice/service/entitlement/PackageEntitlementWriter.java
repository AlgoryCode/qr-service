package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.ael.algoryqrservice.service.PurchaseLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Owns the {@code tbl_user_entitlement} bookkeeping for a purchase: granting package items,
 * keeping their validity window aligned with the purchase and revoking them again.
 */
@Service
@RequiredArgsConstructor
public class PackageEntitlementWriter {

    private static final int MIN_ADDON_QUANTITY = 1;

    private final UserEntitlementRepository entitlementRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final PurchaseLogService purchaseLogService;

    @Transactional
    public void grant(Purchase purchase, Long productId, String productCode, int quantity, boolean unlimited) {
        if (entitlementRepository.findByPurchaseIdAndProductId(purchase.getId(), productId).isPresent()) {
            return;
        }
        entitlementRepository.save(UserEntitlement.builder()
                .userId(purchase.getUserId())
                .productId(productId)
                .productCode(productCode)
                .purchaseId(purchase.getId())
                .totalQuantity(quantity)
                .remainingQuantity(quantity)
                .usedQuantity(0)
                .unlimited(unlimited)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .build());

        purchaseLogService.log(
                purchase.getId(),
                purchase.getUserId(),
                PurchaseLogAction.ENTITLEMENT_GRANTED,
                describeQuantity(quantity, unlimited) + productCode + " hakkı tanımlandı ("
                        + purchase.getStartsAt() + " - " + purchase.getExpiresAt() + ")"
        );
    }

    @Transactional
    public void synchronizePeriod(Purchase purchase) {
        List<UserEntitlement> entitlements = entitlementsOf(purchase);
        entitlements.forEach(entitlement -> applyPeriod(entitlement, purchase));
        entitlementRepository.saveAll(entitlements);
    }

    /**
     * Aligns entitlements with the package contents while preserving already consumed quantities.
     */
    @Transactional
    public void ensureEntitlementsForPackage(Purchase purchase, PlanPackage planPackage) {
        Set<Long> keptProductIds = new HashSet<>();
        for (PlanPackageItem item : planPackage.getItems()) {
            Product product = item.getProduct();
            keptProductIds.add(product.getId());
            UserEntitlement entitlement = findEntitlement(purchase, product);
            if (entitlement == null) {
                grant(purchase, product.getId(), product.getCode(), item.getQuantity(), item.isUnlimited());
                continue;
            }
            entitlement.setProductCode(product.getCode());
            entitlement.setUnlimited(item.isUnlimited());
            applyPeriod(entitlement, purchase);
            applyQuantityKeepingUsage(entitlement, item.getQuantity(), item.isUnlimited());
            entitlementRepository.save(entitlement);
        }
        revokeRemovedItems(purchase, keptProductIds);
    }

    @Transactional
    public void revokeForCancelledPurchase(Purchase purchase) {
        List<UserEntitlement> entitlements = entitlementsOf(purchase);
        for (UserEntitlement entitlement : entitlements) {
            entitlement.setExpiresAt(purchase.getExpiresAt());
            if (!entitlement.isUnlimited()) {
                entitlement.setRemainingQuantity(0);
            }
        }
        entitlementRepository.saveAll(entitlements);
    }

    /**
     * Re-aligns add-on purchases whose entitlement drifted, e.g. a start date in the future
     * or a leftover row pointing at a product that is no longer sold.
     */
    @Transactional
    public void repairAddonEntitlements(Purchase purchase) {
        LocalDateTime now = LocalDateTime.now();
        if (purchase.getStartsAt() != null && purchase.getStartsAt().isAfter(now)) {
            purchase.setStartsAt(now);
            purchaseRepository.save(purchase);
        }
        Product product = productRepository.findByCode(purchase.getPackageCode()).orElse(null);
        if (product == null) {
            return;
        }

        int quantity = addonQuantityOf(purchase);
        UserEntitlement matched = null;
        for (UserEntitlement entitlement : entitlementsOf(purchase)) {
            if (referencesProduct(entitlement, product)) {
                matched = entitlement;
                continue;
            }
            entitlement.setRemainingQuantity(0);
            entitlement.setExpiresAt(purchase.getExpiresAt() != null ? purchase.getExpiresAt() : now);
            entitlementRepository.save(entitlement);
        }

        if (matched == null) {
            grant(purchase, product.getId(), product.getCode(), quantity, false);
            return;
        }
        matched.setProductId(product.getId());
        matched.setProductCode(product.getCode());
        matched.setUnlimited(false);
        applyPeriod(matched, purchase);
        applyQuantityKeepingUsage(matched, quantity, false);
        entitlementRepository.save(matched);
    }

    private void revokeRemovedItems(Purchase purchase, Set<Long> keptProductIds) {
        List<UserEntitlement> removed = entitlementsOf(purchase).stream()
                .filter(entitlement -> !keptProductIds.contains(entitlement.getProductId()))
                .toList();
        for (UserEntitlement entitlement : removed) {
            entitlement.setRemainingQuantity(0);
            entitlement.setExpiresAt(purchase.getExpiresAt());
        }
        entitlementRepository.saveAll(removed);
    }

    private void applyQuantityKeepingUsage(UserEntitlement entitlement, int total, boolean unlimited) {
        if (unlimited) {
            entitlement.setTotalQuantity(total);
            return;
        }
        int used = Math.min(entitlement.getUsedQuantity() == null ? 0 : entitlement.getUsedQuantity(), total);
        entitlement.setUsedQuantity(used);
        entitlement.setTotalQuantity(total);
        entitlement.setRemainingQuantity(Math.max(0, total - used));
    }

    private void applyPeriod(UserEntitlement entitlement, Purchase purchase) {
        entitlement.setStartsAt(purchase.getStartsAt());
        entitlement.setExpiresAt(purchase.getExpiresAt());
    }

    private UserEntitlement findEntitlement(Purchase purchase, Product product) {
        return entitlementRepository.findByPurchaseIdAndProductId(purchase.getId(), product.getId()).orElse(null);
    }

    private List<UserEntitlement> entitlementsOf(Purchase purchase) {
        return entitlementRepository.findByPurchaseIdOrderByProductCodeAsc(purchase.getId());
    }

    private boolean referencesProduct(UserEntitlement entitlement, Product product) {
        return Objects.equals(entitlement.getProductId(), product.getId())
                || Objects.equals(entitlement.getProductCode(), product.getCode());
    }

    private int addonQuantityOf(Purchase purchase) {
        Integer installments = purchase.getInstallmentCount();
        return installments == null || installments < MIN_ADDON_QUANTITY ? MIN_ADDON_QUANTITY : installments;
    }

    private String describeQuantity(int quantity, boolean unlimited) {
        return unlimited ? "Sınırsız " : quantity + " adet ";
    }
}
