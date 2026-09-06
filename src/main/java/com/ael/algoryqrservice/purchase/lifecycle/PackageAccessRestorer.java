package com.ael.algoryqrservice.purchase.lifecycle;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PackageAccessRestorer {

    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository packageRepository;
    private final PackageEntitlementWriter entitlementWriter;

    public Purchase restoreActive(Purchase purchase, int days) {
        LocalDateTime now = AppTime.nowLocal();
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setExpiresAt(now.plusDays(days));
        if (purchase.getStartsAt() == null) {
            purchase.setStartsAt(now);
        }
        purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        purchase.setSubscriptionGraceEndsAt(null);
        purchase.setSubscriptionStatusReason("admin_reactivation");
        purchase.setSubscriptionStatusChangedAt(now);
        purchase.setSubscriptionStatusChangedBy("admin");
        purchase.setCancelAtPeriodEnd(false);
        purchaseRepository.save(purchase);
        entitlementWriter.synchronizePeriod(purchase);
        if (purchase.getPackageId() != null) {
            packageRepository.findByIdWithItems(purchase.getPackageId())
                    .ifPresent(planPackage -> entitlementWriter.ensureEntitlementsForPackage(purchase, planPackage));
        }
        return purchase;
    }
}
