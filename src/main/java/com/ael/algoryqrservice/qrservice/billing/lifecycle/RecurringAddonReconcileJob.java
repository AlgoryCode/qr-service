package com.ael.algoryqrservice.qrservice.billing.lifecycle;

import com.ael.algoryqrservice.qrservice.billing.service.RecurringAddonBilling;
import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Once a day, puts every package subscription's renewal charge back in line with its recurring
 * modules, so a failed or missed sync is retried before the next renewal.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringAddonReconcileJob {

    private final RecurringAddonBilling recurringAddonBilling;
    private final SchedulerLock schedulerLock;

    @Scheduled(cron = "${billing.recurring-addons.reconcile-cron:0 30 4 * * *}", zone = "Europe/Istanbul")
    public void run() {
        schedulerLock.runExclusively("recurring-addon-reconcile", Duration.ofMinutes(30), () -> {
            int fixed = recurringAddonBilling.reconcileAll();
            if (fixed > 0) {
                log.info("Recurring module charges re-synced. count={}", fixed);
            }
        });
    }
}
