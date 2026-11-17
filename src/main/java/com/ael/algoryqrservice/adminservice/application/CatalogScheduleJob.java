package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Starts and ends scheduled price changes and campaigns every few minutes. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogScheduleJob {

    private final CatalogScheduleService service;
    private final SchedulerLock schedulerLock;

    @Scheduled(cron = "${catalog.schedule.cron:0 */5 * * * *}")
    public void run() {
        schedulerLock.runExclusively("catalog-schedule", Duration.ofMinutes(4), () -> {
            int changed = service.runDue();
            if (changed > 0) {
                log.info("catalog_schedule_job changed={}", changed);
            }
        });
    }
}
