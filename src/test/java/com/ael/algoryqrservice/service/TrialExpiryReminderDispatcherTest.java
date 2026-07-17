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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialExpiryReminderDispatcherTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PurchaseReminderRepository purchaseReminderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationPublisherService notificationPublisherService;

    private TrialExpiryReminderDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new TrialExpiryReminderDispatcher(
                purchaseRepository,
                purchaseReminderRepository,
                userRepository,
                notificationPublisherService,
                "https://app.algory.com"
        );
    }

    @Test
    void sendIfNeeded_whenEligibleAndUnsent_thenPersistAndPublishMandatoryReminder() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 19, 9, 0);
        Purchase purchase = Purchase.builder()
                .id(42L)
                .userId(7L)
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .packageName("Dijital Menü PRO")
                .expiresAt(expiresAt)
                .build();
        User user = User.builder()
                .id(7L)
                .firstName("Tarik")
                .lastName("Hamarat")
                .email("tarik@example.com")
                .notifyMarketingEmails(false)
                .build();
        when(purchaseRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(purchase));
        when(purchaseReminderRepository.existsByPurchaseIdAndReminderType(
                42L,
                PurchaseReminderType.PRO_TRIAL_EXPIRY_REMINDER
        )).thenReturn(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        dispatcher.sendIfNeeded(42L);

        ArgumentCaptor<PurchaseReminder> reminderCaptor = ArgumentCaptor.forClass(PurchaseReminder.class);
        verify(purchaseReminderRepository).saveAndFlush(reminderCaptor.capture());
        PurchaseReminder reminder = reminderCaptor.getValue();
        assertThat(reminder.getPurchaseId()).isEqualTo(42L);
        assertThat(reminder.getReminderType()).isEqualTo(PurchaseReminderType.PRO_TRIAL_EXPIRY_REMINDER);
        assertThat(reminder.getEventId()).isEqualTo(dispatcher.deterministicEventId(42L));
        verify(notificationPublisherService).publishTrialExpiryReminder(
                reminder.getEventId(),
                "tarik@example.com",
                "Tarik Hamarat",
                "Dijital Menü PRO",
                "19.07.2026 09:00",
                "https://app.algory.com/dashboard/digital-menu"
        );
    }

    @Test
    void sendIfNeeded_whenAlreadySent_thenDoNotPublishAgain() {
        Purchase purchase = Purchase.builder()
                .id(42L)
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.of(2026, 7, 19, 9, 0))
                .build();
        when(purchaseRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(purchase));
        when(purchaseReminderRepository.existsByPurchaseIdAndReminderType(
                42L,
                PurchaseReminderType.PRO_TRIAL_EXPIRY_REMINDER
        )).thenReturn(true);

        dispatcher.sendIfNeeded(42L);

        verify(purchaseReminderRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(notificationPublisherService, never()).publishTrialExpiryReminder(
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void deterministicEventId_whenCalledRepeatedly_thenReturnSameUuid() {
        assertThat(dispatcher.deterministicEventId(42L))
                .isEqualTo(dispatcher.deterministicEventId(42L))
                .isNotEqualTo(dispatcher.deterministicEventId(43L));
    }
}
