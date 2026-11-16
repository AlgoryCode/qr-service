package com.ael.algoryqrservice.qrservice.analytics;

import com.ael.algoryqrservice.qrservice.analytics.service.SmartReportService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.repository.SmartReportEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartReportListFilterTest {

    @Mock
    private SmartReportEventRepository repository;

    @InjectMocks
    private SmartReportService service;

    @Test
    void createdDateRangeIncludesTheWholeLastDay() {
        PageRequest pageable = PageRequest.of(1, 10);
        when(repository.findByMerchantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Page.empty());

        service.listJobs(1L, "all", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), pageable);

        verify(repository).findByMerchantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                1L, LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 0, 0), pageable);
    }

    @Test
    void withoutDatesKeepsTheUnfilteredQuery() {
        PageRequest pageable = PageRequest.of(0, 5);
        when(repository.findByMerchantIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(Page.empty());

        service.listJobs(1L, "all", null, null, pageable);

        verify(repository).findByMerchantIdOrderByCreatedAtDesc(1L, pageable);
    }

    @Test
    void rejectsAnInvertedRange() {
        assertThatThrownBy(() -> service.listJobs(
                1L, "all", LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 1), PageRequest.of(0, 5)))
                .isInstanceOf(BadRequestException.class);
    }
}
