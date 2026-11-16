package com.ael.algoryqrservice.qrservice.billing.lifecycle;

import com.ael.algoryqrservice.fulfillmentservice.application.service.SubscriptionLifecycleService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Moves due subscriptions on: ended trials, grants and unrenewed paid packages, and expired payment
 * grace periods all fall back to the free package. Also sends trial reminders.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionLifecycleJob {

    private static final int BATCH = 100;

    private final SubscriptionLifecycleService lifecycleService;
    private final SchedulerLock schedulerLock;

    @Scheduled(fixedDelayString = "${billing.lifecycle.fixed-delay:300000}", initialDelay = 60_000)
    public void run() {
        schedulerLock.runExclusively("subscription-lifecycle", Duration.ofMinutes(10), this::processDue);
    }

    @Scheduled(cron = "${billing.lifecycle.reminder-cron:0 0 9 * * *}", zone = "Europe/Istanbul")
    public void remind() {
        schedulerLock.runExclusively("subscription-trial-reminders", Duration.ofMinutes(30), () -> {
            int sent = lifecycleService.remindEndingTrials();
            if (sent > 0) {
                log.info("Trial reminders emitted. count={}", sent);
            }
        });
    }

    /** One transaction per merchant, so one broken account does not hold the others back. */
    int processDue() {
        List<ActivePackage> due = lifecycleService.findDue(BATCH);
        int moved = 0;
        for (ActivePackage active : due) {
            try {
                if (lifecycleService.downgradeToFree(active.merchantId(),
                        SubscriptionLifecycleService.dueReason(active))) {
                    moved++;
                }
            } catch (RuntimeException exception) {
                log.error("Subscription lifecycle step failed. merchantId={} package={}",
                        active.merchantId(), active.packageCode(), exception);
            }
        }
        if (moved > 0) {
            log.info("Subscriptions moved to free. count={}", moved);
        }
        return moved;
    }
}
