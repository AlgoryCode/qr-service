package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.qrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.qrservice.model.enums.AccountingSourceType;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.repository.UserAccountingEntryRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Books a finished device session (time, items, less discount) as income ("GELIR") in the merchant's accounting, once per session. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsoleSessionAccounting {

    private final ConsoleSessionRepository sessionRepository;
    private final GameConsoleRepository consoleRepository;
    private final UserAccountingEntryRepository accountingRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFinished(ConsoleSessionFinishedEvent event) {
        try {
            record(event.sessionId());
        } catch (Exception ex) {
            log.error("Failed to record accounting CONSOLE_SESSION for sessionId={}", event.sessionId(), ex);
        }
    }

    public void record(Long sessionId) {
        if (sessionId == null
                || accountingRepository.existsBySourceTypeAndSourceConsoleSessionId(
                        AccountingSourceType.CONSOLE_SESSION, sessionId)) {
            return;
        }
        ConsoleSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isDeleted() || session.isActive()
                || session.getAmount() == null || session.getAmount().signum() <= 0) {
            return;
        }
        String consoleName = consoleRepository.findById(session.getConsoleId())
                .map(console -> console.getName())
                .orElse("Cihaz");
        LocalDateTime now = AppTime.nowLocal();
        UserAccountingEntry entry = UserAccountingEntry.builder()
                .merchantId(session.getMerchantId())
                .entryType(AccountingEntryType.GELIR)
                .title(truncate("Cihaz kiralama - " + consoleName, 200))
                .amount(session.getAmount())
                .occurredAt(session.getEndedAt())
                .note(truncate(note(session), 500))
                .sourceType(AccountingSourceType.CONSOLE_SESSION)
                .sourceConsoleSessionId(session.getId())
                .orderAmount(session.getAmount())
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            accountingRepository.save(entry);
        } catch (DataIntegrityViolationException duplicate) {
            // Another worker booked the same session first; the unique index keeps it single.
            log.info("Console session already booked. sessionId={}", sessionId);
        }
    }

    static String note(ConsoleSession session) {
        StringBuilder note = new StringBuilder()
                .append(session.getDurationMinutes() == null ? 0 : session.getDurationMinutes())
                .append(" dk");
        if (session.getHourlyRate() != null) {
            note.append(" × ").append(plain(session.getHourlyRate())).append(" TL/saat");
        }
        if (session.getItemsAmount() != null && session.getItemsAmount().signum() > 0) {
            note.append(" · Ürünler: ").append(plain(session.getItemsAmount())).append(" TL");
        }
        if (session.getDiscountAmount() != null && session.getDiscountAmount().signum() > 0) {
            note.append(" · İndirim: ").append(plain(session.getDiscountAmount())).append(" TL");
        }
        if (session.getPaymentMethod() != null) {
            note.append(" · Ödeme: ").append(session.getPaymentMethod().label());
        }
        if (session.getCustomerName() != null && !session.getCustomerName().isBlank()) {
            note.append(" · Müşteri: ").append(session.getCustomerName().trim());
        }
        return note.toString();
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
