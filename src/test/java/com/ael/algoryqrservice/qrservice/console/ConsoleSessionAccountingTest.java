package com.ael.algoryqrservice.qrservice.console;

import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionAccounting;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.qrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.qrservice.model.enums.AccountingSourceType;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.repository.UserAccountingEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsoleSessionAccountingTest {

    private static final LocalDateTime ENDED = LocalDateTime.of(2026, 11, 20, 22, 10);

    @Mock
    private ConsoleSessionRepository sessionRepository;
    @Mock
    private GameConsoleRepository consoleRepository;
    @Mock
    private UserAccountingEntryRepository accountingRepository;

    private ConsoleSessionAccounting accounting;

    @BeforeEach
    void setUp() {
        accounting = new ConsoleSessionAccounting(sessionRepository, consoleRepository, accountingRepository);
        GameConsole console = GameConsole.builder().merchantId(13L).branchId(1L).name("PS5 - 1").build();
        console.setId(7L);
        when(consoleRepository.findById(7L)).thenReturn(Optional.of(console));
    }

    private ConsoleSession finished(String amount) {
        ConsoleSession session = ConsoleSession.builder()
                .merchantId(13L)
                .branchId(1L)
                .consoleId(7L)
                .customerName("Ahmet")
                .hourlyRate(new BigDecimal("120.00"))
                .startedAt(ENDED.minusMinutes(96))
                .endedAt(ENDED)
                .durationMinutes(96)
                .amount(new BigDecimal(amount))
                .build();
        session.setId(500L);
        return session;
    }

    @Test
    void record_writesIncomeEntryLinkedToSession() {
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(finished("192.00")));

        accounting.record(500L);

        ArgumentCaptor<UserAccountingEntry> saved = ArgumentCaptor.forClass(UserAccountingEntry.class);
        verify(accountingRepository).save(saved.capture());
        UserAccountingEntry entry = saved.getValue();
        assertThat(entry.getMerchantId()).isEqualTo(13L);
        assertThat(entry.getEntryType()).isEqualTo(AccountingEntryType.GELIR);
        assertThat(entry.getSourceType()).isEqualTo(AccountingSourceType.CONSOLE_SESSION);
        assertThat(entry.getSourceConsoleSessionId()).isEqualTo(500L);
        assertThat(entry.getAmount()).isEqualByComparingTo("192.00");
        assertThat(entry.getOccurredAt()).isEqualTo(ENDED);
        assertThat(entry.getTitle()).isEqualTo("Cihaz kiralama - PS5 - 1");
        assertThat(entry.getNote()).isEqualTo("96 dk × 120 TL/saat · Müşteri: Ahmet");
    }

    @Test
    void record_whenAlreadyBooked_doesNothing() {
        when(accountingRepository.existsBySourceTypeAndSourceConsoleSessionId(AccountingSourceType.CONSOLE_SESSION, 500L))
                .thenReturn(true);

        accounting.record(500L);

        verify(accountingRepository, never()).save(any());
    }

    @Test
    void record_whenSessionStillOpenOrFree_doesNothing() {
        ConsoleSession open = finished("10.00");
        open.setEndedAt(null);
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(open));
        accounting.record(500L);

        when(sessionRepository.findById(500L)).thenReturn(Optional.of(finished("0.00")));
        accounting.record(500L);

        verify(accountingRepository, never()).save(any());
    }

    @Test
    void record_whenConcurrentDuplicate_swallowsUniqueViolation() {
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(finished("50.00")));
        when(accountingRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatCode(() -> accounting.record(500L)).doesNotThrowAnyException();
    }

    @Test
    void note_listsItemsDiscountAndPayment() {
        ConsoleSession session = finished("230.00");
        session.setItemsAmount(new BigDecimal("48.00"));
        session.setDiscountAmount(new BigDecimal("10.00"));
        session.setPaymentMethod(com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod.CARD);

        when(sessionRepository.findById(500L)).thenReturn(Optional.of(session));

        accounting.record(500L);

        ArgumentCaptor<UserAccountingEntry> saved = ArgumentCaptor.forClass(UserAccountingEntry.class);
        verify(accountingRepository).save(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo("96 dk × 120 TL/saat · Ürünler: 48 TL · İndirim: 10 TL · Ödeme: Kart · Müşteri: Ahmet");
    }
}
