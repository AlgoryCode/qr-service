package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.repository.SmartReportEventRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class SmartReportCompletionNotifier {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SmartReportEventRepository smartReportEventRepository;
    private final UserRepository userRepository;
    private final NotificationPublisherService notificationPublisherService;
    private final String appUrl;

    public SmartReportCompletionNotifier(
            SmartReportEventRepository smartReportEventRepository,
            UserRepository userRepository,
            NotificationPublisherService notificationPublisherService,
            @Value("${app.url:http://localhost:3000}") String appUrl
    ) {
        this.smartReportEventRepository = smartReportEventRepository;
        this.userRepository = userRepository;
        this.notificationPublisherService = notificationPublisherService;
        this.appUrl = appUrl;
    }

    @Transactional
    public void markFailedNotified(UUID processId) {
        SmartReportEvent event = smartReportEventRepository.findById(processId).orElse(null);
        if (event == null || event.getNotificationSentAt() != null) {
            return;
        }
        event.setNotificationSentAt(LocalDateTime.now());
        smartReportEventRepository.save(event);
    }

    @Transactional
    public void sendReadyEmail(UUID processId, String reportTitle) {
        SmartReportEvent event = smartReportEventRepository.findById(processId).orElse(null);
        if (event == null || event.getNotificationSentAt() != null) {
            return;
        }

        User user = userRepository.findById(event.getUserId()).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Smart report ready email skipped; user missing. processId={}", processId);
            markNotificationSent(event);
            return;
        }
        if (!user.isNotifyEmailImportant()) {
            markNotificationSent(event);
            return;
        }

        String title = reportTitle == null || reportTitle.isBlank() ? "Akıllı Rapor" : reportTitle.trim();
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = user.getEmail();
        }
        String menuName = event.getMenuName();
        if (menuName == null || menuName.isBlank()) {
            menuName = "Menü";
        }

        notificationPublisherService.publishSmartReportReady(
                deterministicEventId(processId),
                user.getEmail(),
                displayName,
                menuName,
                title,
                event.getFromDate().format(PERIOD_FORMATTER),
                event.getToDate().format(PERIOD_FORMATTER),
                appUrl + "/dashboard/dijital-menu/akilli-raporlar/" + processId
        );
        markNotificationSent(event);
    }

    private void markNotificationSent(SmartReportEvent event) {
        event.setNotificationSentAt(LocalDateTime.now());
        smartReportEventRepository.save(event);
    }

    UUID deterministicEventId(UUID processId) {
        return UUID.nameUUIDFromBytes(("SMART_REPORT_READY:" + processId).getBytes(StandardCharsets.UTF_8));
    }
}
