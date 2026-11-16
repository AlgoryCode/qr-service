package com.ael.algoryqrservice.qrservice.config;

import com.ael.algoryqrservice.qrservice.billing.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.qrservice.menu.service.MenuPublicAccessService;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Closes purchase records whose time is up. It does not end the merchant's package: the
 * subscription lifecycle job does that and moves the merchant to the free package.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PackageExpirationScheduler {

    private final PurchaseExpiryService purchaseExpiryService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final PurchaseRepository purchaseRepository;
    private final SchedulerLock schedulerLock;

    @Scheduled(fixedRate = 300_000)
    public void expirePackages() {
        schedulerLock.runExclusivelyInTransaction("package-expiration", Duration.ofMinutes(4), this::expireDue);
    }

    void expireDue() {
        List<Purchase> duePurchases = purchaseRepository.findByStatusAndExpiresAtBefore(
                PurchaseStatus.ACTIVE,
                LocalDateTime.now()
        );
        purchaseExpiryService.expireAllDue();
        suspendExpiredGracePeriods();

        List<Long> affectedMerchantIds = duePurchases.stream().map(Purchase::getMerchantId).distinct().toList();
        menuPublicAccessService.syncForMerchants(affectedMerchantIds);
    }

    private void suspendExpiredGracePeriods() {
        LocalDateTime now = LocalDateTime.now();
        List<Purchase> dueGracePeriods = purchaseRepository
                .findByPaymentStyleAndSubscriptionStatusAndSubscriptionGraceEndsAtBefore(
                        PaymentStyle.SUBSCRIPTION, SubscriptionStatus.GRACE_PERIOD, now);
        for (Purchase purchase : dueGracePeriods) {
            purchase.setSubscriptionStatus(SubscriptionStatus.SUSPENDED);
            purchase.setSubscriptionStatusReason("grace_period_expired");
            purchase.setSubscriptionStatusChangedAt(now);
            purchase.setSubscriptionStatusChangedBy("system_scheduler");
            purchaseRepository.save(purchase);
            log.warn("Subscription suspended after grace period. purchaseId={} graceEndsAt={}",
                    purchase.getId(), purchase.getSubscriptionGraceEndsAt());
            menuPublicAccessService.syncForMerchant(purchase.getMerchantId());
        }
    }
}
