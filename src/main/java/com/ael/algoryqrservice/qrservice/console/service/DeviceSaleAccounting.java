package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.qrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.qrservice.model.enums.AccountingSourceType;
import com.ael.algoryqrservice.qrservice.repository.UserAccountingEntryRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Books membership sales and tournament entry fees as income ("GELIR"), once per {@code ref}. Balance top-ups and
 * gift money are never booked; sessions are booked by {@link ConsoleSessionAccounting}.
 */
@Component
@RequiredArgsConstructor
public class DeviceSaleAccounting {

    private final UserAccountingEntryRepository accountingRepository;

    public void book(Long merchantId, String ref, String title, BigDecimal amount, LocalDateTime occurredAt, String note) {
        if (amount == null || amount.signum() <= 0 || accountingRepository.existsBySourceDeviceRef(ref)) {
            return;
        }
        LocalDateTime now = AppTime.nowLocal();
        accountingRepository.save(UserAccountingEntry.builder()
                .merchantId(merchantId)
                .entryType(AccountingEntryType.GELIR)
                .title(truncate(title, 200))
                .amount(amount)
                .occurredAt(occurredAt == null ? now : occurredAt)
                .note(note == null ? null : truncate(note, 500))
                .sourceType(AccountingSourceType.DEVICE_SALE)
                .sourceDeviceRef(ref)
                .orderAmount(amount)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
