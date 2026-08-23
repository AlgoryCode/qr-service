package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.SmartReportQuotaProperties;
import com.ael.algoryqrservice.config.SmartReportRabbitProperties;
import com.ael.algoryqrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.repository.SmartReportEventRepository;
import com.ael.algoryqrservice.repository.SmartReportResultRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartReportServiceTest {

    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private SmartReportEventRepository smartReportEventRepository;
    @Mock
    private SmartReportResultRepository smartReportResultRepository;
    @Mock
    private UserEntitlementRepository userEntitlementRepository;
    @Mock
    private SmartReportCompletionNotifier smartReportCompletionNotifier;

    private SmartReportService service;

    @BeforeEach
    void setUp() {
        SmartReportRabbitProperties rabbitProperties = new SmartReportRabbitProperties();
        SmartReportQuotaProperties quotaProperties = new SmartReportQuotaProperties();
        quotaProperties.setQuotaLimit(5);
        service = new SmartReportService(
                analyticsService,
                rabbitTemplate,
                rabbitProperties,
                quotaProperties,
                smartReportEventRepository,
                smartReportResultRepository,
                userEntitlementRepository,
                smartReportCompletionNotifier,
                new ObjectMapper().registerModule(new JavaTimeModule())
        );
        when(smartReportEventRepository.countByUserIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(0L);
        when(userEntitlementRepository.findByUserIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(smartReportEventRepository.save(any(SmartReportEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueue_whenMenu_thenStoresMenuFields() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 2);
        when(analyticsService.getMenuReport(5L, 9L, from, to)).thenReturn(visitReport(5L, "Ogle", 2L, "Kadikoy"));

        service.enqueue(5L, 9L, from, to, "tr", null);

        ArgumentCaptor<SmartReportEvent> captor = ArgumentCaptor.forClass(SmartReportEvent.class);
        verify(smartReportEventRepository).save(captor.capture());
        assertThat(captor.getValue().getMenuId()).isEqualTo(5L);
        assertThat(captor.getValue().getMenuName()).isEqualTo("Ogle");
        assertThat(captor.getValue().getBranchId()).isEqualTo(2L);
        assertThat(captor.getValue().getBranchName()).isEqualTo("Kadikoy");
    }

    @Test
    void enqueueForBranch_whenBranchTotal_thenStoresBranchWithoutMenu() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 2);
        when(analyticsService.getBranchReport(2L, null, 9L, from, to))
                .thenReturn(visitReport(null, null, 2L, "Kadikoy"));

        service.enqueueForBranch(2L, null, 9L, from, to, "tr", null);

        ArgumentCaptor<SmartReportEvent> captor = ArgumentCaptor.forClass(SmartReportEvent.class);
        verify(smartReportEventRepository).save(captor.capture());
        assertThat(captor.getValue().getMenuId()).isNull();
        assertThat(captor.getValue().getMenuName()).isNull();
        assertThat(captor.getValue().getBranchId()).isEqualTo(2L);
        assertThat(captor.getValue().getBranchName()).isEqualTo("Kadikoy");
    }

    private AnalyticsDtos.MenuAnalyticsReportResponse visitReport(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName
    ) {
        return new AnalyticsDtos.MenuAnalyticsReportResponse(
                menuId,
                menuName,
                branchId,
                branchName,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 2),
                new AnalyticsDtos.ReportKpis(0L, 0L, 0L, 0L, 0d),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new AnalyticsDtos.FunnelCounts(0L, 0L, 0L),
                new AnalyticsDtos.ReportFeedback(
                        new AnalyticsDtos.MenuFeedbackSummary(BigDecimal.ZERO, 0L, List.of(), List.of()),
                        new AnalyticsDtos.ProductFeedbackSummary(
                                BigDecimal.ZERO, 0L, List.of(), List.of(), List.of(), List.of())
                )
        );
    }
}
