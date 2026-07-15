package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.service.UserPackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PackageExpirationScheduler {

    private final EntitlementService entitlementService;
    private final UserPackageService userPackageService;

    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void expirePackages() {
        entitlementService.expireDuePurchases();
        userPackageService.restoreFreePackagesAfterProExpiry();
    }
}
