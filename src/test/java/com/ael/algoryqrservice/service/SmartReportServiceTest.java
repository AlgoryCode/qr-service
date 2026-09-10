package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.config.SmartReportQuotaProperties;
import com.ael.algoryqrservice.config.SmartReportRabbitProperties;
import com.ael.algoryqrservice.exception.TooManyRequestsException;
import com.ael.algoryqrservice.messaging.dto.SmartReportGenerateMessage;
import com.ael.algoryqrservice.messaging.dto.SmartReportStatusMessage;
import com.ael.algoryqrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.model.SmartReportResult;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.dto.FulfillmentConsumeResult;
import com.ael.algoryqrservice.model.dto.SmartReportDtos;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SmartReportRabbitProperties rabbitProperties = new SmartReportRabbitProperties();
        rabbitProperties.setQueue("smart_report.generate");
        SmartReportQuotaProperties quotaProperties = new SmartReportQuotaProperties();
        quotaProperties.setQuotaLimit(5);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new SmartReportService(
                analyticsService,
                new SmartReportModelInputBuilder(),
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
                objectMapper
        );
        org.mockito.Mockito.lenient()
                .when(smartReportEventRepository.save(any(SmartReportEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(smartReportEventRepository.countByUserIdAndCreatedAtGreaterThanEqual(any(), any()))
                .thenReturn(0L);
        org.mockito.Mockito.lenient()
                .when(fulfillmentGateService.remainingQuantity(any(), anyString(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(0);
        org.mockito.Mockito.lenient()
                .when(productRepository.findByCode(anyString()))
                .thenReturn(Optional.empty());
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
    void enqueueForBranch_whenBranchTotal_thenPublishesCanonicalInput() throws Exception {
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

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(anyString(), messageCaptor.capture());
        SmartReportGenerateMessage payload = objectMapper.readValue(
                messageCaptor.getValue().getBody(),
                SmartReportGenerateMessage.class
        );
        assertThat(payload.input()).isNotNull();
        assertThat(payload.input().meta().branchId()).isEqualTo(2L);
        assertThat(payload.input().revenue()).isNotNull();
        assertThat(payload.input().visits()).isNotNull();
        assertThat(payload.input().waiter()).isNotNull();
    }

    @Test
    void enqueueForBranch_whenFreeExhaustedAndPaidCredit_thenConsumeAddon() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 2);
        when(smartReportEventRepository.countByUserIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(5L);
        when(fulfillmentGateService.remainingQuantity(eq(9L), eq(CatalogProducts.SMART_REPORTING), eq(true)))
                .thenReturn(1);
        when(fulfillmentGateService.consumeAddon(
                eq(9L),
                eq(CatalogProducts.SMART_REPORTING),
                eq(1),
                eq(FulfillmentReferenceType.FEATURE),
                isNull()
        )).thenReturn(new FulfillmentConsumeResult(1, 11L, 22L));
        when(analyticsService.getBranchReport(2L, null, 9L, from, to))
                .thenReturn(visitReport(null, null, 2L, "Kadikoy"));
        when(analyticsService.getBranchRevenueReport(2L, null, 9L, from, to))
                .thenReturn(emptyRevenue(null, null, 2L, "Kadikoy"));
        when(analyticsService.getBranchWaiterPerformanceReport(2L, null, 9L, from, to))
                .thenReturn(emptyWaiter(null, null, 2L, "Kadikoy"));

        service.enqueueForBranch(2L, 9L, from, to, "tr", null);

        verify(fulfillmentGateService).consumeAddon(
                eq(9L),
                eq(CatalogProducts.SMART_REPORTING),
                eq(1),
                eq(FulfillmentReferenceType.FEATURE),
                isNull()
        );
        verify(smartReportEventRepository).save(any(SmartReportEvent.class));
    }

    @Test
    void enqueueForBranch_whenFreeAndPaidExhausted_thenTooManyRequests() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 2);
        when(smartReportEventRepository.countByUserIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(5L);
        when(fulfillmentGateService.remainingQuantity(eq(9L), eq(CatalogProducts.SMART_REPORTING), eq(true)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.enqueueForBranch(2L, 9L, from, to, "tr", null))
                .isInstanceOf(TooManyRequestsException.class);
        verify(fulfillmentGateService, never()).consumeAddon(any(), anyString(), anyInt(), any(), any());
        verify(rabbitTemplate, never()).send(anyString(), any(Message.class));
    }

    @Test
    void applyStatusEvent_whenCompletedWithStructuredResult_thenStoresJsonWithSections() {
        UUID jobId = UUID.fromString("281f830b-ec6c-4fa6-b6e1-04d8c66f1549");
        SmartReportEvent existing = SmartReportEvent.builder()
                .processId(jobId)
                .userId(9L)
                .branchId(2L)
                .branchName("Kadikoy")
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 7, 2))
                .locale("tr")
                .status(SmartReportEvent.STATUS_PROCESSING)
                .build();
        when(smartReportEventRepository.findById(jobId)).thenReturn(Optional.of(existing));
        when(smartReportResultRepository.findByProcessId(jobId)).thenReturn(Optional.empty());
        when(smartReportResultRepository.save(any(SmartReportResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SmartReportDtos.AiSmartReportResult structured = new SmartReportDtos.AiSmartReportResult(
                "Sube Ozeti",
                "Ciro artti",
                List.of(new SmartReportDtos.AiReportSection("channels", "Satış kanalları", "Uber one cikti")),
                "# Sube Ozeti\n\nCiro artti",
                "gpt",
                "v1",
                null
        );
        service.applyStatusEvent(new SmartReportStatusMessage(
                jobId,
                "completed",
                structured,
                "# Sube Ozeti\n\nCiro artti",
                null,
                null,
                Instant.parse("2026-07-02T12:00:00Z"),
                Instant.parse("2026-07-02T12:00:00Z")
        ));

        ArgumentCaptor<SmartReportResult> resultCaptor = ArgumentCaptor.forClass(SmartReportResult.class);
        verify(smartReportResultRepository).save(resultCaptor.capture());
        String stored = resultCaptor.getValue().getResultText();
        assertThat(stored).startsWith("{");
        assertThat(stored).contains("\"title\":\"Sube Ozeti\"");
        assertThat(stored).contains("\"key\":\"channels\"");
        assertThat(stored).contains("\"rawMarkdown\"");
        assertThat(existing.getStatus()).isEqualTo(SmartReportEvent.STATUS_COMPLETED);
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
