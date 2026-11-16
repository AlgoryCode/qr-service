package com.ael.algoryqrservice.qrservice.billing.lifecycle;

import com.ael.algoryqrservice.qrservice.billing.service.PlanChangeService;
import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Applies due NEXT_PERIOD plan changes ahead of the subscription renewal they modify. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanChangeScheduler {

    private final PlanChangeService planChangeService;
    private final SchedulerLock schedulerLock;

    @Scheduled(fixedDelayString = "${billing.plan-change.fixed-delay:300000}", initialDelay = 90_000)
    public void run() {
        schedulerLock.runExclusively("plan-change", Duration.ofMinutes(10), this::executeDue);
    }

    /** One transaction per change, so one broken account does not hold the others back. */
    void executeDue() {
        for (Long id : planChangeService.findDueScheduledIds()) {
            try {
                planChangeService.executeScheduled(id);
            } catch (RuntimeException exception) {
                log.error("Scheduled plan change failed. planChangeId={}", id, exception);
            }
        }
    }
}
