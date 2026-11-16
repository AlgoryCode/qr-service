package com.ael.algoryqrservice.qrservice.analytics;

import com.ael.algoryqrservice.qrservice.analytics.dto.SmartReportDtos;
import com.ael.algoryqrservice.qrservice.analytics.service.SmartReportService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.qrservice.repository.SmartReportEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartReportUsageTest {

    @Mock
    private SmartReportEventRepository repository;

    @InjectMocks
    private SmartReportService service;

    @Test
    void splitsReportsByCreditSourcePerDay() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Istanbul"));
        when(repository.findByMerchantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(eq(1L), any())).thenReturn(List.of(
                event(today, SmartReportEvent.CREDIT_PAID),
                event(today, SmartReportEvent.CREDIT_BONUS),
                event(today.minusDays(2), SmartReportEvent.CREDIT_FREE),
                event(today.minusDays(3), null)
        ));

        SmartReportDtos.SmartReportUsageResponse usage = service.getUsage(1L, 7);

        assertThat(usage.daily()).hasSize(7);
        assertThat(usage.from()).isEqualTo(today.minusDays(6));
        assertThat(usage.totals()).isEqualTo(new SmartReportDtos.UsageTotals(2, 1, 1, 4));
        assertThat(usage.daily().get(6)).isEqualTo(new SmartReportDtos.UsageDay(today, 0, 1, 1));
        assertThat(usage.items()).hasSize(4);
    }

    @Test
    void acceptsOnlySevenOrThirtyDays() {
        assertThatThrownBy(() -> service.getUsage(1L, 14)).isInstanceOf(BadRequestException.class);
    }

    private static SmartReportEvent event(LocalDate day, String creditSource) {
        SmartReportEvent event = new SmartReportEvent();
        event.setProcessId(UUID.randomUUID());
        event.setMerchantId(1L);
        event.setCreatedAt(day.atTime(12, 0));
        event.setFromDate(day);
        event.setToDate(day);
        event.setCreditSource(creditSource);
        return event;
    }
}
