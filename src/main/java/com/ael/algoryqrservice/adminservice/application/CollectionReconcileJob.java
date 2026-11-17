package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Once a day, opens, escalates and closes collection cases from each merchant's billing standing. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollectionReconcileJob {

    private final CollectionService collectionService;
    private final SchedulerLock schedulerLock;

    @Scheduled(cron = "${billing.collections.reconcile-cron:0 0 5 * * *}", zone = "Europe/Istanbul")
    public void run() {
        schedulerLock.runExclusively("collection-reconcile", Duration.ofMinutes(30), () -> {
            int changed = collectionService.reconcile();
            if (changed > 0) {
                log.info("Collection cases updated. count={}", changed);
            }
        });
    }
}
