package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.messaging.fulfillment.FulfillmentQueuePublisher;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.MenuPublicAccessService;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PackageExpirationScheduler {

    private final PurchaseExpiryService purchaseExpiryService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final PurchaseRepository purchaseRepository;
    private final FulfillmentQueuePublisher fulfillmentQueuePublisher;

    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void expirePackages() {
        List<Purchase> duePurchases = purchaseRepository.findByStatusAndExpiresAtBefore(
                PurchaseStatus.ACTIVE,
                LocalDateTime.now()
        );
        cascadeExpireFulfillments(duePurchases);
        purchaseExpiryService.expireAllDue();

        suspendExpiredGracePeriods();

        List<Long> affectedUserIds = duePurchases.stream().map(Purchase::getUserId).distinct().toList();
        menuPublicAccessService.syncForUsers(affectedUserIds);
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
            menuPublicAccessService.syncForUser(purchase.getUserId());
        }
    }

    private void cascadeExpireFulfillments(List<Purchase> duePurchases) {
        for (Purchase purchase : duePurchases) {
            fulfillmentQueuePublisher.publishRevoked(purchase);
        }
    }
}
