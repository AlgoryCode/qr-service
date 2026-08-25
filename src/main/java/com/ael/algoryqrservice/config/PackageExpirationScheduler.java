package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.service.FulfillmentGrantService;
import com.ael.algoryqrservice.service.MenuPublicAccessService;
import com.ael.algoryqrservice.service.PackageActivationService;
import com.ael.algoryqrservice.service.PlanChangeService;
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

    private final EntitlementService entitlementService;
    private final PackageActivationService packageActivationService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final PurchaseRepository purchaseRepository;
    private final PlanChangeService planChangeService;
    private final FulfillmentGrantService fulfillmentGrantService;

    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void expirePackages() {
        planChangeService.executeDueScheduled();
        List<Purchase> duePurchases = purchaseRepository.findByStatusAndExpiresAtBefore(
                PurchaseStatus.ACTIVE,
                LocalDateTime.now()
        );
        cascadeExpireFulfillments(duePurchases);
        entitlementService.expireDuePurchases();
        packageActivationService.syncSubscriptionStateForUsers(
                duePurchases.stream().map(Purchase::getUserId).distinct().toList()
        );
        packageActivationService.syncAfterPaidExpiry();
        menuPublicAccessService.syncForUsers(
                duePurchases.stream().map(Purchase::getUserId).distinct().toList()
        );
    }

    private void cascadeExpireFulfillments(List<Purchase> duePurchases) {
        for (Purchase purchase : duePurchases) {
            try {
                fulfillmentGrantService.expireFulfillmentForPurchase(purchase.getId());
                if (purchase.getPurchaseType() != PurchaseType.ADD_ON && purchase.getPackageId() != null) {
                    fulfillmentGrantService.expireAddonFulfillmentsForUser(
                            purchase.getUserId(), purchase.getPackageId()
                    );
                }
            } catch (Exception e) {
                log.error("Fulfillment expiry cascade failed for purchaseId={}: {}",
                        purchase.getId(), e.getMessage());
            }
        }
    }
}
