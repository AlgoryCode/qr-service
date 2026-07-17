package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.TrialExpiryReminderDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialExpiryReminderSchedulerTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private TrialExpiryReminderDispatcher trialExpiryReminderDispatcher;

    private TrialExpiryReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TrialExpiryReminderScheduler(
                purchaseRepository,
                trialExpiryReminderDispatcher,
                "Europe/Istanbul"
        );
    }

    @Test
    void sendTrialExpiryReminders_whenThreeDaysRemain_thenDispatchEachPurchaseOnce() {
        LocalDate currentDate = LocalDate.of(2026, 7, 16);
        Purchase first = Purchase.builder().id(10L).build();
        Purchase second = Purchase.builder().id(11L).build();
        when(purchaseRepository.findByPurchaseTypeAndStatusAndExpiresAtGreaterThanEqualAndExpiresAtLessThan(
                PurchaseType.TRIAL,
                PurchaseStatus.ACTIVE,
                LocalDate.of(2026, 7, 19).atStartOfDay(),
                LocalDate.of(2026, 7, 20).atStartOfDay()
        )).thenReturn(List.of(first, second));

        scheduler.sendTrialExpiryReminders(currentDate);

        verify(trialExpiryReminderDispatcher).sendIfNeeded(10L);
        verify(trialExpiryReminderDispatcher).sendIfNeeded(11L);
    }
}
