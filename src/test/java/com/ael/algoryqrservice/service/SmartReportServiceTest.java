package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.SmartReportQuotaProperties;
import com.ael.algoryqrservice.config.SmartReportRabbitProperties;
import com.ael.algoryqrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.SmartReportEventRepository;
import com.ael.algoryqrservice.repository.SmartReportResultRepository;
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
    private FulfillmentDetailRepository fulfillmentDetailRepository;
    @Mock
    private FulfillmentUsageLogRepository fulfillmentUsageLogRepository;
    @Mock
    private FulfillmentGateService fulfillmentGateService;
    @Mock
    private ProductRepository productRepository;
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
                fulfillmentDetailRepository,
                fulfillmentUsageLogRepository,
                fulfillmentGateService,
                productRepository,
                smartReportCompletionNotifier,
                new ObjectMapper().registerModule(new JavaTimeModule())
        );
        when(smartReportEventRepository.countByUserIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(0L);
        when(smartReportEventRepository.save(any(SmartReportEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueue_whenMenu_thenStoresMenuFields() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 2);
        when(analyticsService.getMenuReport(5L, 9L, from, to)).thenReturn(visitReport(5L, "Ogle", 2L, "Kadikoy"));
        when(analyticsService.getMenuRevenueReport(5L, 9L, from, to)).thenReturn(emptyRevenue(5L, "Ogle", 2L, "Kadikoy"));
        when(analyticsService.getMenuWaiterPerformanceReport(5L, 9L, from, to)).thenReturn(emptyWaiter(5L, "Ogle", 2L, "Kadikoy"));

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
        when(analyticsService.getBranchRevenueReport(2L, null, 9L, from, to))
                .thenReturn(emptyRevenue(null, null, 2L, "Kadikoy"));
        when(analyticsService.getBranchWaiterPerformanceReport(2L, null, 9L, from, to))
                .thenReturn(emptyWaiter(null, null, 2L, "Kadikoy"));

        service.enqueueForBranch(2L, 9L, from, to, "tr", null);

        ArgumentCaptor<SmartReportEvent> captor = ArgumentCaptor.forClass(SmartReportEvent.class);
        verify(smartReportEventRepository).save(captor.capture());
        assertThat(captor.getValue().getMenuId()).isNull();
        assertThat(captor.getValue().getMenuName()).isNull();
        assertThat(captor.getValue().getBranchId()).isEqualTo(2L);
        assertThat(captor.getValue().getBranchName()).isEqualTo("Kadikoy");
        verify(analyticsService).getBranchReport(2L, null, 9L, from, to);
        verify(analyticsService).getBranchRevenueReport(2L, null, 9L, from, to);
        verify(analyticsService).getBranchWaiterPerformanceReport(2L, null, 9L, from, to);
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

    private AnalyticsDtos.MenuRevenueReportResponse emptyRevenue(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName
    ) {
        return new AnalyticsDtos.MenuRevenueReportResponse(
                menuId,
                menuName,
                branchId,
                branchName,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 2),
                new AnalyticsDtos.RevenueKpis(BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO, "TRY"),
                List.of(),
                List.of(),
                List.of(),
                new AnalyticsDtos.RevenueSpotlight(null, null, null),
                List.of(),
                new AnalyticsDtos.UnsoldCatalog(0L, List.of()),
                new AnalyticsDtos.RevenuePaymentBreakdown(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "TRY"
                ),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private AnalyticsDtos.MenuWaiterPerformanceReportResponse emptyWaiter(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName
    ) {
        return new AnalyticsDtos.MenuWaiterPerformanceReportResponse(
                menuId,
                menuName,
                branchId,
                branchName,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 2),
                new AnalyticsDtos.WaiterPerformanceKpis(
                        0L, 0L, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, "TRY"
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
