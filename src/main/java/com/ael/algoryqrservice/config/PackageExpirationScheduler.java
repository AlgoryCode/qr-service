package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.service.MenuPublicAccessService;
import com.ael.algoryqrservice.service.PackageActivationService;
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

    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void expirePackages() {
        List<Purchase> duePurchases = purchaseRepository.findByStatusAndExpiresAtBefore(
                PurchaseStatus.ACTIVE,
                LocalDateTime.now()
        );
        entitlementService.expireDuePurchases();
        packageActivationService.restoreFreePackagesAfterPaidExpiry();
        menuPublicAccessService.syncForUsers(
                duePurchases.stream().map(Purchase::getUserId).distinct().toList()
        );
    }
}
