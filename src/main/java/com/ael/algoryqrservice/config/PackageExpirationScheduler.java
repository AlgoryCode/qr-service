package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PackageExpirationScheduler {

    private final EntitlementService entitlementService;

    @Scheduled(fixedRate = 300_000)
    public void expirePackages() {
        entitlementService.expireDuePurchases();
    }
}
