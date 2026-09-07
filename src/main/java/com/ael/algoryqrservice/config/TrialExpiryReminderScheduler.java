package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.service.TrialExpiryReminderDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class TrialExpiryReminderScheduler {

    private final TrialLogRepository trialLogRepository;
    private final TrialExpiryReminderDispatcher trialExpiryReminderDispatcher;
    private final String reminderZone;

    public TrialExpiryReminderScheduler(
            TrialLogRepository trialLogRepository,
            TrialExpiryReminderDispatcher trialExpiryReminderDispatcher,
            @Value("${trial.reminder.zone:Europe/Istanbul}") String reminderZone
    ) {
        this.trialLogRepository = trialLogRepository;
        this.trialExpiryReminderDispatcher = trialExpiryReminderDispatcher;
        this.reminderZone = reminderZone;
    }

    @Scheduled(
            cron = "${trial.reminder.cron:0 0 9 * * *}",
            zone = "${trial.reminder.zone:Europe/Istanbul}"
    )
    public void sendTrialExpiryReminders() {
        sendTrialExpiryReminders(LocalDate.now(ZoneId.of(reminderZone)));
    }

    void sendTrialExpiryReminders(LocalDate currentDate) {
        LocalDate reminderDate = currentDate.plusDays(3);
        List<TrialLog> activeLogs =
                trialLogRepository.findByStatusAndEndsAtGreaterThanEqualAndEndsAtLessThan(
                        TrialLogStatus.ACTIVE,
                        reminderDate.atStartOfDay(),
                        reminderDate.plusDays(1).atStartOfDay()
                );
        activeLogs.forEach(log -> trialExpiryReminderDispatcher.sendIfNeeded(log.getId()));

        List<TrialLog> endedLogs = trialLogRepository
                .findByStatusAndEndsAtGreaterThanEqualAndEndsAtLessThan(
                        TrialLogStatus.ENDED,
                        currentDate.minusDays(1).atStartOfDay(),
                        currentDate.plusDays(1).atStartOfDay()
                );
        endedLogs.forEach(log -> trialExpiryReminderDispatcher.sendExpiredIfNeeded(log.getId()));
    }
}
