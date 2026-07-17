package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.PushNotificationProperties;
import com.ael.algoryqrservice.messaging.dto.NotificationRecipientsMessage;
import com.ael.algoryqrservice.messaging.dto.NotificationRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPublisherService {

    private static final String MESSAGE_TYPE_PASSWORD_RESET = "PASSWORD_RESET";
    private static final String MESSAGE_TYPE_PRO_TRIAL_EXPIRY_REMINDER = "PRO_TRIAL_EXPIRY_REMINDER";

    private final RabbitTemplate rabbitTemplate;
    private final PushNotificationProperties pushNotificationProperties;

    @Value("${app.service-name:qr-service}")
    private String serviceName;

    public void publishPasswordChangeCode(String email, String userName, String code, int validityMinutes) {
        publishVerificationCode(
                email,
                userName,
                code,
                validityMinutes,
                pushNotificationProperties.getPasswordResetSubject()
        );
        log.info("Password change code notification queued. email={}", maskEmail(email));
    }

    public void publishEmailChangeCode(String email, String userName, String code, int validityMinutes) {
        publishVerificationCode(
                email,
                userName,
                code,
                validityMinutes,
                pushNotificationProperties.getEmailChangeSubject()
        );
        log.info("Email change code notification queued. email={}", maskEmail(email));
    }

    public void publishTrialExpiryReminder(
            UUID eventId,
            String email,
            String userName,
            String packageName,
            String expiresAt,
            String upgradeUrl
    ) {
        Map<String, Object> templateData = Map.of(
                "userName", userName,
                "packageName", packageName,
                "expiresAt", expiresAt,
                "daysRemaining", 3,
                "upgradeUrl", upgradeUrl
        );
        NotificationRequestMessage message = new NotificationRequestMessage(
                eventId,
                pushNotificationProperties.getChannels(),
                serviceName,
                MESSAGE_TYPE_PRO_TRIAL_EXPIRY_REMINDER,
                new NotificationRecipientsMessage(email, List.of(), List.of()),
                null,
                templateData,
                false
        );
        rabbitTemplate.convertAndSend(
                pushNotificationProperties.getMessaging().getExchange(),
                pushNotificationProperties.getMessaging().getRoutingKey(),
                message
        );
        log.info("Trial expiry reminder queued. eventId={}, email={}", eventId, maskEmail(email));
    }

    private void publishVerificationCode(
            String email,
            String userName,
            String code,
            int validityMinutes,
            String subject
    ) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("userName", userName);
        templateData.put("code", code);
        templateData.put("validityMinutes", validityMinutes);

        NotificationRequestMessage message = new NotificationRequestMessage(
                UUID.randomUUID(),
                pushNotificationProperties.getChannels(),
                serviceName,
                MESSAGE_TYPE_PASSWORD_RESET,
                new NotificationRecipientsMessage(email, List.of(), List.of()),
                subject,
                templateData,
                true
        );

        rabbitTemplate.convertAndSend(
                pushNotificationProperties.getMessaging().getExchange(),
                pushNotificationProperties.getMessaging().getRoutingKey(),
                message
        );
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return "*".repeat(local.length()) + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
