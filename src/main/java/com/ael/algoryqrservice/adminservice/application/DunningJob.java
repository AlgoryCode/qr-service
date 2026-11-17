package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Every morning, e-mails members whose package is about to end or whose renewal is still unpaid. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DunningJob {

    private final DunningService dunningService;
    private final SchedulerLock schedulerLock;

    @Scheduled(cron = "${billing.reminders.cron:0 0 10 * * *}", zone = "Europe/Istanbul")
    public void run() {
        schedulerLock.runExclusively("billing-reminders", Duration.ofMinutes(30), () -> {
            int sent = dunningService.runScheduled();
            if (sent > 0) {
                log.info("Billing reminders sent. count={}", sent);
            }
        });
    }
}
