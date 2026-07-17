package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.TrialExpiryReminderDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class TrialExpiryReminderScheduler {

    private final PurchaseRepository purchaseRepository;
    private final TrialExpiryReminderDispatcher trialExpiryReminderDispatcher;
    private final String reminderZone;

    public TrialExpiryReminderScheduler(
            PurchaseRepository purchaseRepository,
            TrialExpiryReminderDispatcher trialExpiryReminderDispatcher,
            @Value("${trial.reminder.zone:Europe/Istanbul}") String reminderZone
    ) {
        this.purchaseRepository = purchaseRepository;
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
        List<Purchase> purchases =
                purchaseRepository.findByPurchaseTypeAndStatusAndExpiresAtGreaterThanEqualAndExpiresAtLessThan(
                        PurchaseType.TRIAL,
                        PurchaseStatus.ACTIVE,
                        reminderDate.atStartOfDay(),
                        reminderDate.plusDays(1).atStartOfDay()
                );
        purchases.forEach(purchase -> trialExpiryReminderDispatcher.sendIfNeeded(purchase.getId()));
    }
}
