package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.PurchaseReminder;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.PurchaseReminderType;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseReminderRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
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

    private final PurchaseRepository purchaseRepository;
    private final PurchaseReminderRepository purchaseReminderRepository;
    private final UserRepository userRepository;
    private final NotificationPublisherService notificationPublisherService;
    private final String appUrl;

    public TrialExpiryReminderDispatcher(
            PurchaseRepository purchaseRepository,
            PurchaseReminderRepository purchaseReminderRepository,
            UserRepository userRepository,
            NotificationPublisherService notificationPublisherService,
            @Value("${app.url:http://localhost:3000}") String appUrl
    ) {
        this.purchaseRepository = purchaseRepository;
        this.purchaseReminderRepository = purchaseReminderRepository;
        this.userRepository = userRepository;
        this.notificationPublisherService = notificationPublisherService;
        this.appUrl = appUrl;
    }

    @Transactional
    public void sendIfNeeded(Long purchaseId) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId).orElse(null);
        if (!isEligible(purchase)) {
            return;
        }
        if (purchaseReminderRepository.existsByPurchaseIdAndReminderType(purchaseId, REMINDER_TYPE)) {
            return;
        }
        User user = userRepository.findById(purchase.getUserId()).orElseThrow();
        UUID eventId = deterministicEventId(purchaseId);
        purchaseReminderRepository.saveAndFlush(new PurchaseReminder(purchaseId, REMINDER_TYPE, eventId));
        notificationPublisherService.publishTrialExpiryReminder(
                eventId,
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                purchase.getPackageName(),
                purchase.getExpiresAt().format(EXPIRY_FORMATTER),
                appUrl + "/dashboard/digital-menu"
        );
    }

    UUID deterministicEventId(Long purchaseId) {
        String source = REMINDER_TYPE.name() + ":" + purchaseId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isEligible(Purchase purchase) {
        return purchase != null
                && purchase.getPurchaseType() == PurchaseType.TRIAL
                && purchase.getStatus() == PurchaseStatus.ACTIVE
                && purchase.getExpiresAt() != null;
    }
}
