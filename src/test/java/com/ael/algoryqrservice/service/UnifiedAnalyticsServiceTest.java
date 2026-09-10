package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedAnalyticsServiceTest {

    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private BranchService branchService;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private UnifiedAnalyticsAggregates aggregates;

    private UnifiedAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new UnifiedAnalyticsService(analyticsService, branchService, menuRepository, aggregates);
    }

    @Test
    void getBranchSummary_whenEmptyPeriod_thenReturnsCoverageAndZeroishOverview() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 7);
        stubScope();
        stubEmptyReports(from, to);
        when(aggregates.buildOrders(any(), eq(from), eq(to))).thenReturn(emptyOrders(from, to));
        when(aggregates.buildTables(any(), eq(from), eq(to))).thenReturn(
                new AnalyticsDtos.TablesAnalytics(List.of(), null, null, 0L, 0L, null, null, null));
        when(aggregates.countOpenBills(any())).thenReturn(0L);
        when(aggregates.countActiveTables(any())).thenReturn(4L);
        when(aggregates.occupancyRatio(0L, 4L)).thenReturn(0d);
        when(aggregates.topTables(any(), any(Integer.class))).thenReturn(List.of());
        when(aggregates.topTablesLimit()).thenReturn(5);

        AnalyticsDtos.SummaryAnalyticsResponse summary = service.getBranchSummary(1L, null, 9L, from, to);

        assertThat(summary.coverage().kitchenMetrics()).isTrue();
        assertThat(summary.coverage().discounts()).isTrue();
        assertThat(summary.coverage().shifts()).isTrue();
        assertThat(summary.coverage().cartToOrderFunnel()).isTrue();
        assertThat(summary.coverage().coversPerTable()).isTrue();
        assertThat(summary.overview().grossRevenue()).isEqualByComparingTo("0");
        assertThat(summary.period().previousFrom()).isEqualTo(LocalDate.of(2026, 2, 22));
        assertThat(summary.period().previousTo()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(summary.qrTeaser().sessions()).isZero();
    }

    @Test
    void getBranchFull_whenDataPresent_thenSectionsNonNullAndCoverageFalse() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 1);
        stubScope();
        stubFilledReports(from, to);

        AnalyticsDtos.OrdersAnalytics orders = new AnalyticsDtos.OrdersAnalytics(
                List.of(new AnalyticsDtos.StatusCount("CONFIRMED", 2L)),
                List.of(),
                List.of(),
                2.5d,
                0d,
                new BigDecimal("120.00"),
                2L,
                2L,
                0L
        );
        AnalyticsDtos.TablesAnalytics tables = new AnalyticsDtos.TablesAnalytics(
                List.of(new AnalyticsDtos.TableAnalyticsRow(
                        7L, "Masa 1", new BigDecimal("240.00"), 2L, 1L,
                        new BigDecimal("240.00"), 45d, 1L, 1L, 1L, 4, 2, new BigDecimal("120.00"))),
                45d,
                1d,
                1L,
                1L,
                50d,
                25d,
                new BigDecimal("120.00")
        );
        when(aggregates.buildOrders(any(), eq(from), eq(to))).thenReturn(orders);
        when(aggregates.buildTables(any(), eq(from), eq(to))).thenReturn(tables);
        when(aggregates.countOpenBills(any())).thenReturn(1L);
        when(aggregates.countActiveTables(any())).thenReturn(4L);
        when(aggregates.occupancyRatio(1L, 4L)).thenReturn(25d);
        when(aggregates.buildProducts(any(), any(), eq(from), eq(to))).thenReturn(
                new AnalyticsDtos.ProductsAnalytics(List.of(), List.of(), List.of(), List.of(), List.of(),
                        new AnalyticsDtos.UnsoldCatalog(0L, List.of())));
        when(aggregates.buildFinance(any(), any(), eq(from), eq(to))).thenReturn(
                new AnalyticsDtos.FinanceAnalytics(
                        new BigDecimal("240.00"), BigDecimal.ZERO, new BigDecimal("100.00"),
                        new BigDecimal("140.00"), BigDecimal.ZERO, new BigDecimal("240.00"),
                        new BigDecimal("240.00"), new BigDecimal("240.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        List.of(), null, "TRY"));
        when(aggregates.buildCancellations(any(), eq(1L), eq(from), eq(to), eq(orders))).thenReturn(
                new AnalyticsDtos.CancellationsAnalytics(List.of(), List.of(), 0L, 0L));
        when(aggregates.buildKitchen(any(), eq(from), eq(to))).thenReturn(
                new AnalyticsDtos.KitchenAnalytics(
                        new AnalyticsDtos.KitchenStageKpis(5d, 4d, 2d, 11d, 0L, 0L, 1L, 0L, 0d),
                        List.of(),
                        20L
                ));
        when(aggregates.buildShifts(eq(1L), eq(from), eq(to))).thenReturn(
                new AnalyticsDtos.ShiftsAnalytics(List.of(), BigDecimal.ZERO, 0L));
        when(aggregates.buildCustomers(any(), eq(from), eq(to))).thenReturn(
                new AnalyticsDtos.CustomersAnalytics(1L, 1L, 3L, 1L, 2L, 1L, 50d));
        when(aggregates.buildDrillDownHints(any(), eq(from), eq(to))).thenReturn(List.of(
                new AnalyticsDtos.DrillDownLink(10L, 20L, 7L, 3L, 11L, from.atTime(12, 0), new BigDecimal("100.00"))
        ));

        AnalyticsDtos.FullAnalyticsResponse full = service.getBranchFull(1L, null, 9L, from, to);

        assertThat(full.coverage().kitchenMetrics()).isTrue();
        assertThat(full.dashboard()).isNotNull();
        assertThat(full.orders().completedOrders()).isEqualTo(2L);
        assertThat(full.staff().rows()).hasSize(1);
        assertThat(full.products()).isNotNull();
        assertThat(full.tables().perTable()).hasSize(1);
        assertThat(full.qr().kpis().sessions()).isEqualTo(3L);
        assertThat(full.finance().grossRevenue()).isEqualByComparingTo("240.00");
        assertThat(full.cancellations()).isNotNull();
        assertThat(full.kitchen()).isNotNull();
        assertThat(full.shifts()).isNotNull();
        assertThat(full.customers().anonymousSessions()).isEqualTo(3L);
        assertThat(full.drillDownHints()).hasSize(1);
        assertThat(full.dashboard().overview().occupancyRatio()).isEqualTo(25d);
    }

    @Test
    void getBranchSummary_whenPeriodTooLong_thenThrows() {
        assertThatThrownBy(() -> service.getBranchSummary(
                1L, null, 9L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("90");
    }

    private void stubScope() {
        Branch branch = Branch.builder().id(1L).name("Merkez").build();
        when(branchService.requireOwnedForUser(1L, 9L)).thenReturn(branch);
        Menu menu = Menu.builder().menuId(5L).businessName("Lokanta").branchId(1L).build();
        when(menuRepository.findByBranchIdAndDeletedFalse(1L)).thenReturn(List.of(menu));
    }

    private void stubEmptyReports(LocalDate from, LocalDate to) {
        AnalyticsDtos.MenuRevenueReportResponse revenue = emptyRevenue(from, to);
        when(analyticsService.getBranchRevenueReport(eq(1L), eq(null), eq(9L), any(), any()))
                .thenReturn(revenue);
        when(analyticsService.getBranchWaiterPerformanceReport(eq(1L), eq(null), eq(9L), eq(from), eq(to)))
                .thenReturn(emptyWaiter(from, to));
        when(analyticsService.getBranchReport(eq(1L), eq(null), eq(9L), eq(from), eq(to)))
                .thenReturn(emptyVisit(from, to));
    }

    private void stubFilledReports(LocalDate from, LocalDate to) {
        AnalyticsDtos.RevenuePaymentBreakdown pb = new AnalyticsDtos.RevenuePaymentBreakdown(
                new BigDecimal("100.00"),
                new BigDecimal("140.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("240.00"),
                BigDecimal.ZERO,
                new BigDecimal("240.00"),
                "TRY"
        );
        AnalyticsDtos.MenuRevenueReportResponse revenue = new AnalyticsDtos.MenuRevenueReportResponse(
                5L, "Lokanta", 1L, "Merkez", from, to,
                new AnalyticsDtos.RevenueKpis(new BigDecimal("240.00"), 2L, 4L, new BigDecimal("120.00"), "TRY"),
                List.of(new AnalyticsDtos.DailyRevenuePoint(from, new BigDecimal("240.00"), 2L)),
                List.of(new AnalyticsDtos.RevenueProduct(11L, "Adana", 2L, new BigDecimal("200.00"))),
                List.of(new AnalyticsDtos.RevenueCategory(1L, "Kebap", 2L, new BigDecimal("200.00"))),
                new AnalyticsDtos.RevenueSpotlight(null, null, null),
                List.of(new AnalyticsDtos.HourlyRevenuePoint(12, new BigDecimal("240.00"), 2L)),
                new AnalyticsDtos.UnsoldCatalog(0L, List.of()),
                pb,
                List.of(),
                List.of(),
                List.of()
        );
        AnalyticsDtos.MenuRevenueReportResponse previous = emptyRevenue(
                from.minusDays(1), from.minusDays(1));
        when(analyticsService.getBranchRevenueReport(eq(1L), eq(null), eq(9L), eq(from), eq(to)))
                .thenReturn(revenue);
        when(analyticsService.getBranchRevenueReport(eq(1L), eq(null), eq(9L), eq(from.minusDays(1)), eq(from.minusDays(1))))
                .thenReturn(previous);
        when(analyticsService.getBranchWaiterPerformanceReport(eq(1L), eq(null), eq(9L), eq(from), eq(to)))
                .thenReturn(new AnalyticsDtos.MenuWaiterPerformanceReportResponse(
                        5L, "Lokanta", 1L, "Merkez", from, to,
                        new AnalyticsDtos.WaiterPerformanceKpis(
                                1L, 1L, 1L, new BigDecimal("240.00"), 4L,
                                BigDecimal.ZERO, BigDecimal.ZERO, 1L, "TRY"),
                        List.of(new AnalyticsDtos.WaiterPerformanceRow(
                                3L, "Ali", 1L, 2L, new BigDecimal("140.00"), BigDecimal.ZERO,
                                BigDecimal.ZERO, 1L, new BigDecimal("140.00"), 58d, 50d, 50d, true, List.of())),
                        List.of(),
                        List.of(),
                        List.of()
                ));
        when(analyticsService.getBranchReport(eq(1L), eq(null), eq(9L), eq(from), eq(to)))
                .thenReturn(new AnalyticsDtos.MenuAnalyticsReportResponse(
                        5L, "Lokanta", 1L, "Merkez", from, to,
                        new AnalyticsDtos.ReportKpis(3L, 3L, 5L, 2L, 1.6d),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        new AnalyticsDtos.FunnelCounts(3L, 2L, 5L),
                        null
                ));
    }

    private AnalyticsDtos.MenuRevenueReportResponse emptyRevenue(LocalDate from, LocalDate to) {
        return new AnalyticsDtos.MenuRevenueReportResponse(
                5L, "Lokanta", 1L, "Merkez", from, to,
                new AnalyticsDtos.RevenueKpis(BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO, "TRY"),
                List.of(), List.of(), List.of(),
                new AnalyticsDtos.RevenueSpotlight(null, null, null),
                List.of(),
                new AnalyticsDtos.UnsoldCatalog(0L, List.of()),
                new AnalyticsDtos.RevenuePaymentBreakdown(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "TRY"),
                List.of(), List.of(), List.of()
        );
    }

    private AnalyticsDtos.MenuWaiterPerformanceReportResponse emptyWaiter(LocalDate from, LocalDate to) {
        return new AnalyticsDtos.MenuWaiterPerformanceReportResponse(
                5L, "Lokanta", 1L, "Merkez", from, to,
                new AnalyticsDtos.WaiterPerformanceKpis(
                        0L, 0L, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, "TRY"),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private AnalyticsDtos.MenuAnalyticsReportResponse emptyVisit(LocalDate from, LocalDate to) {
        return new AnalyticsDtos.MenuAnalyticsReportResponse(
                5L, "Lokanta", 1L, "Merkez", from, to,
                new AnalyticsDtos.ReportKpis(0L, 0L, 0L, 0L, 0d),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new AnalyticsDtos.FunnelCounts(0L, 0L, 0L),
                null
        );
    }

    private AnalyticsDtos.OrdersAnalytics emptyOrders(LocalDate from, LocalDate to) {
        return new AnalyticsDtos.OrdersAnalytics(
                List.of(), List.of(), List.of(), 0d, 0d, BigDecimal.ZERO, 0L, 0L, 0L);
    }
}
