package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.WaiterBranchBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class WaiterBranchBackfillRunner implements ApplicationRunner {

    private final WaiterBranchBackfillService waiterBranchBackfillService;
    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!appProperties.getSeed().isWaiterBranchBackfill()) {
            return;
        }
        try {
            int updated = waiterBranchBackfillService.backfillWaiterBranches();
            log.info("Waiter branch backfill completed, updated={}", updated);
        } catch (Exception exception) {
            log.warn("Waiter branch backfill failed: {}", exception.getMessage());
        }
    }
}
