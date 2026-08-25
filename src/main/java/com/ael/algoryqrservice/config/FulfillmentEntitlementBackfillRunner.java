package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.FulfillmentMigrationService;
import com.ael.algoryqrservice.service.MenuPublicAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(40)
public class FulfillmentEntitlementBackfillRunner implements ApplicationRunner {

    private final FulfillmentMigrationService fulfillmentMigrationService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!appProperties.getSeed().isFulfillmentEntitlementBackfill()) {
            return;
        }
        try {
            int migrated = fulfillmentMigrationService.backfillAllActiveUsers();
            menuPublicAccessService.syncAllMenuOwners();
            log.info("Fulfillment entitlement backfill completed for {} users", migrated);
        } catch (Exception exception) {
            log.warn("Fulfillment entitlement backfill failed: {}", exception.getMessage());
        }
    }
}
