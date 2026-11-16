package com.ael.algoryqrservice.qrservice.analytics;

import com.ael.algoryqrservice.qrservice.analytics.dto.SmartReportDtos;
import com.ael.algoryqrservice.qrservice.analytics.service.SmartReportFeedbackService;
import com.ael.algoryqrservice.qrservice.analytics.service.SmartReportService;
import com.ael.algoryqrservice.qrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.qrservice.model.SmartReportFeedback;
import com.ael.algoryqrservice.qrservice.repository.SmartReportEventRepository;
import com.ael.algoryqrservice.qrservice.repository.SmartReportFeedbackRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartReportFeedbackBonusTest {

    private static final long MERCHANT = 7L;
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 9);

    @Mock
    private SmartReportEventRepository eventRepository;
    @Mock
    private SmartReportFeedbackRepository feedbackRepository;
    @Mock
    private SmartReportService smartReportService;

    private SmartReportFeedbackService service;
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SmartReportFeedbackService(eventRepository, feedbackRepository, smartReportService, new ObjectMapper());
        SmartReportEvent report = new SmartReportEvent();
        report.setProcessId(jobId);
        report.setMerchantId(MERCHANT);
        report.setStatus(SmartReportEvent.STATUS_COMPLETED);
        when(eventRepository.findByProcessIdAndMerchantId(jobId, MERCHANT)).thenReturn(Optional.of(report));
        lenient().when(smartReportService.currentBonusWeek()).thenReturn(WEEK);
        lenient().when(feedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstFeedbackOfTheWeekEarnsTheBonus() {
        when(feedbackRepository.findByProcessIdAndMerchantId(jobId, MERCHANT)).thenReturn(Optional.empty());
        when(feedbackRepository.existsByMerchantIdAndBonusWeek(MERCHANT, WEEK)).thenReturn(false);

        SmartReportDtos.FeedbackResponse response = service.save(MERCHANT, jobId, yes());

        assertThat(response.bonusGranted()).isTrue();
    }

    @Test
    void secondFeedbackInTheSameWeekEarnsNothing() {
        when(feedbackRepository.findByProcessIdAndMerchantId(jobId, MERCHANT)).thenReturn(Optional.empty());
        when(feedbackRepository.existsByMerchantIdAndBonusWeek(MERCHANT, WEEK)).thenReturn(true);

        SmartReportDtos.FeedbackResponse response = service.save(MERCHANT, jobId, yes());

        assertThat(response.bonusGranted()).isFalse();
    }

    @Test
    void editingAnAnswerDoesNotEarnAgain() {
        SmartReportFeedback existing = SmartReportFeedback.builder().processId(jobId).merchantId(MERCHANT).build();
        when(feedbackRepository.findByProcessIdAndMerchantId(jobId, MERCHANT)).thenReturn(Optional.of(existing));

        SmartReportDtos.FeedbackResponse response = service.save(MERCHANT, jobId, yes());

        assertThat(response.bonusGranted()).isFalse();
        verify(feedbackRepository, never()).existsByMerchantIdAndBonusWeek(any(), any());
    }

    @Test
    void likedReportDropsFollowUpAnswers() {
        when(feedbackRepository.findByProcessIdAndMerchantId(jobId, MERCHANT)).thenReturn(Optional.empty());
        SmartReportDtos.FeedbackRequest request = new SmartReportDtos.FeedbackRequest(
                "YES", "NOT_FIT", List.of("Ciro"), List.of("Personel"), "LONGER", "güzel");

        SmartReportDtos.FeedbackResponse response = service.save(MERCHANT, jobId, request);

        assertThat(response.recommendationsFit()).isNull();
        assertThat(response.detailLevel()).isNull();
        assertThat(response.unclearSections()).isEmpty();
        assertThat(response.focusAreas()).isEmpty();
        assertThat(response.note()).isEqualTo("güzel");
    }

    private static SmartReportDtos.FeedbackRequest yes() {
        return new SmartReportDtos.FeedbackRequest("YES", null, null, null, null, null);
    }
}
