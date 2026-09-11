package com.ael.algoryqrservice.purchase.lifecycle;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.FulfillmentGrantService;
import com.ael.algoryqrservice.service.MenuPublicAccessService;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PackagePeriodExtender {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 3650;

    private final PurchaseRepository purchaseRepository;
    private final FulfillmentGrantService fulfillmentGrantService;
    private final PackageEntitlementWriter entitlementWriter;
    private final MenuPublicAccessService menuPublicAccessService;
    private final PurchaseLogService purchaseLogService;

    public Purchase extend(Purchase purchase, int days) {
        validateDays(days);
        if (purchase.getPurchaseType() == PurchaseType.ADD_ON) {
            throw new BadRequestException("Eklenti vadesi host paket ile birlikte uzatilir");
        }
        LocalDateTime now = AppTime.nowLocal();
        LocalDateTime base = purchase.getExpiresAt() != null && purchase.getExpiresAt().isAfter(now)
                ? purchase.getExpiresAt()
                : now;
        purchase.setExpiresAt(base.plusDays(days));
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        purchase.setSubscriptionGraceEndsAt(null);
        purchase.setSubscriptionStatusReason("admin_extension");
        purchase.setSubscriptionStatusChangedAt(now);
        purchase.setSubscriptionStatusChangedBy("admin");
        purchase.setCancelAtPeriodEnd(false);
        if (purchase.getStartsAt() == null) {
            purchase.setStartsAt(now);
        }
        purchaseRepository.save(purchase);
        alignAccess(purchase);
        alignAddons(purchase);
        menuPublicAccessService.syncForUser(purchase.getUserId());
        purchaseLogService.log(
                purchase.getId(),
                purchase.getUserId(),
                PurchaseLogAction.PURCHASE_EXTENDED,
                purchase.getPackageName() + " paketi admin tarafindan " + days + " gun uzatildi"
        );
        return purchase;
    }

    public void alignAccess(Purchase purchase) {
        fulfillmentGrantService.extendPurchasePeriod(purchase);
        entitlementWriter.synchronizePeriod(purchase);
    }

    private void alignAddons(Purchase host) {
        if (host.getPackageId() == null || host.getUserId() == null) {
            return;
        }
        List<Purchase> addons = purchaseRepository.findByUserIdAndStatusAndPurchaseType(
                host.getUserId(),
                PurchaseStatus.ACTIVE,
                PurchaseType.ADD_ON
        );
        for (Purchase addon : addons) {
            if (!host.getPackageId().equals(addon.getPackageId())) {
                continue;
            }
            addon.setExpiresAt(host.getExpiresAt());
            purchaseRepository.save(addon);
            alignAccess(addon);
        }
    }

    private void validateDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BadRequestException("days " + MIN_DAYS + " ile " + MAX_DAYS + " arasinda olmalidir");
        }
    }
}
