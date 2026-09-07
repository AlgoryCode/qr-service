package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PurchaseReminder;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.PurchaseReminderType;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.PurchaseReminderRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class TrialExpiryReminderDispatcher {

    private static final PurchaseReminderType REMINDER_TYPE =
            PurchaseReminderType.PRO_TRIAL_EXPIRY_REMINDER;
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final TrialLogRepository trialLogRepository;
    private final PurchaseReminderRepository purchaseReminderRepository;
    private final UserRepository userRepository;
    private final NotificationPublisherService notificationPublisherService;
    private final String appUrl;

    public TrialExpiryReminderDispatcher(
            TrialLogRepository trialLogRepository,
            PurchaseReminderRepository purchaseReminderRepository,
            UserRepository userRepository,
            NotificationPublisherService notificationPublisherService,
            @Value("${app.url:http://localhost:3000}") String appUrl
    ) {
        this.trialLogRepository = trialLogRepository;
        this.purchaseReminderRepository = purchaseReminderRepository;
        this.userRepository = userRepository;
        this.notificationPublisherService = notificationPublisherService;
        this.appUrl = appUrl;
    }

    @Transactional
    public void sendIfNeeded(Long trialLogId) {
        sendIfNeeded(trialLogId, REMINDER_TYPE, false);
    }

    @Transactional
    public void sendExpiredIfNeeded(Long trialLogId) {
        sendIfNeeded(trialLogId, PurchaseReminderType.PRO_TRIAL_EXPIRED, true);
    }

    private void sendIfNeeded(Long trialLogId, PurchaseReminderType reminderType, boolean expired) {
        TrialLog trialLog = trialLogRepository.findById(trialLogId).orElse(null);
        if (!isEligible(trialLog, expired)) {
            return;
        }
        if (purchaseReminderRepository.existsByPurchaseIdAndReminderType(trialLogId, reminderType)) {
            return;
        }
        User user = userRepository.findById(trialLog.getUserId()).orElseThrow();
        UUID eventId = deterministicEventId(trialLogId, reminderType);
        purchaseReminderRepository.saveAndFlush(new PurchaseReminder(trialLogId, reminderType, eventId));
        if (expired) {
            notificationPublisherService.publishTrialExpired(
                    eventId, user.getEmail(), user.getDisplayName(), trialLog.getPackageCode(),
                    trialLog.getEndsAt().format(EXPIRY_FORMATTER), appUrl + "/dashboard/abonelik"
            );
            return;
        }
        notificationPublisherService.publishTrialExpiryReminder(
                eventId,
                user.getEmail(),
                user.getDisplayName(),
                trialLog.getPackageCode(),
                trialLog.getEndsAt().format(EXPIRY_FORMATTER),
                appUrl + "/dashboard/digital-menu"
        );
    }

    UUID deterministicEventId(Long trialLogId) {
        return deterministicEventId(trialLogId, REMINDER_TYPE);
    }

    private UUID deterministicEventId(Long trialLogId, PurchaseReminderType reminderType) {
        String source = reminderType.name() + ":" + trialLogId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isEligible(TrialLog trialLog, boolean expired) {
        return trialLog != null
                && trialLog.getEndsAt() != null
                && trialLog.getStatus() == (expired ? TrialLogStatus.ENDED : TrialLogStatus.ACTIVE);
    }
}
