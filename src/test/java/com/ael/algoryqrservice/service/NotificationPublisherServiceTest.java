package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.PushNotificationProperties;
import com.ael.algoryqrservice.messaging.dto.NotificationRequestMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishTrialExpiryReminder_whenCalled_thenPublishCompleteTransactionalMessage() {
        PushNotificationProperties properties = new PushNotificationProperties();
        NotificationPublisherService publisher = new NotificationPublisherService(rabbitTemplate, properties);
        ReflectionTestUtils.setField(publisher, "serviceName", "qr-service");
        UUID eventId = UUID.fromString("a35f46f0-b69d-3519-bf11-cb95b9130d19");

        publisher.publishTrialExpiryReminder(
                eventId,
                "tarik@example.com",
                "Tarik Hamarat",
                "Dijital Menü PRO",
                "19.07.2026 09:00",
                "https://app.algory.com/dashboard/digital-menu"
        );

        ArgumentCaptor<NotificationRequestMessage> messageCaptor =
                ArgumentCaptor.forClass(NotificationRequestMessage.class);
        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                exchangeCaptor.capture(),
                routingKeyCaptor.capture(),
                messageCaptor.capture()
        );
        NotificationRequestMessage message = messageCaptor.getValue();
        assertThat(exchangeCaptor.getValue()).isEqualTo("push-notification-exchange");
        assertThat(routingKeyCaptor.getValue()).isEqualTo("push-notification.request");
        assertThat(message.eventId()).isEqualTo(eventId);
        assertThat(message.messageType()).isEqualTo("PRO_TRIAL_EXPIRY_REMINDER");
        assertThat(message.recipients().email()).isEqualTo("tarik@example.com");
        assertThat(message.kvkkApproved()).isFalse();
        assertThat(message.templateData()).containsEntry("userName", "Tarik Hamarat");
        assertThat(message.templateData()).containsEntry("packageName", "Dijital Menü PRO");
        assertThat(message.templateData()).containsEntry("expiresAt", "19.07.2026 09:00");
        assertThat(message.templateData()).containsEntry("daysRemaining", 3);
        assertThat(message.templateData()).containsEntry(
                "upgradeUrl",
                "https://app.algory.com/dashboard/digital-menu"
        );
    }
}
