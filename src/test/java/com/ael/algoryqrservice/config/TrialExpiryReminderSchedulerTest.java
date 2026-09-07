package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.TrialLogRepository;
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
    private TrialLogRepository trialLogRepository;

    @Mock
    private TrialExpiryReminderDispatcher trialExpiryReminderDispatcher;

    private TrialExpiryReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TrialExpiryReminderScheduler(
                trialLogRepository,
                trialExpiryReminderDispatcher,
                "Europe/Istanbul"
        );
    }

    @Test
    void sendTrialExpiryReminders_whenThreeDaysRemain_thenDispatchEachLogOnce() {
        LocalDate currentDate = LocalDate.of(2026, 7, 16);
        TrialLog first = TrialLog.builder().id(10L).build();
        TrialLog second = TrialLog.builder().id(11L).build();
        when(trialLogRepository.findByStatusAndEndsAtGreaterThanEqualAndEndsAtLessThan(
                TrialLogStatus.ACTIVE,
                LocalDate.of(2026, 7, 19).atStartOfDay(),
                LocalDate.of(2026, 7, 20).atStartOfDay()
        )).thenReturn(List.of(first, second));
        when(trialLogRepository.findByStatusAndEndsAtGreaterThanEqualAndEndsAtLessThan(
                TrialLogStatus.ENDED,
                LocalDate.of(2026, 7, 15).atStartOfDay(),
                LocalDate.of(2026, 7, 17).atStartOfDay()
        )).thenReturn(List.of());

        scheduler.sendTrialExpiryReminders(currentDate);

        verify(trialExpiryReminderDispatcher).sendIfNeeded(10L);
        verify(trialExpiryReminderDispatcher).sendIfNeeded(11L);
    }
}
