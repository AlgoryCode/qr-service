package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnifiedAnalyticsService {

    private static final int MAX_PERIOD_DAYS = 90;
    private static final int TEASER_LIMIT = 5;

    private final AnalyticsService analyticsService;
    private final BranchService branchService;
    private final MenuRepository menuRepository;
    private final UnifiedAnalyticsAggregates aggregates;

    @Transactional(readOnly = true)
    public AnalyticsDtos.SummaryAnalyticsResponse getBranchSummary(
            Long branchId,
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        validatePeriod(from, to);
        AnalyticsDtos.AnalyticsPeriod period = buildPeriod(from, to);
        Collection<Long> menuIds = resolveMenuIds(branchId, menuId, ownerId);

        AnalyticsDtos.MenuRevenueReportResponse revenue = analyticsService.getBranchRevenueReport(
                branchId, menuId, ownerId, from, to);
        AnalyticsDtos.MenuRevenueReportResponse previousRevenue = analyticsService.getBranchRevenueReport(
                branchId, menuId, ownerId, period.previousFrom(), period.previousTo());
        AnalyticsDtos.MenuWaiterPerformanceReportResponse waiter = analyticsService.getBranchWaiterPerformanceReport(
                branchId, menuId, ownerId, from, to);
        AnalyticsDtos.MenuAnalyticsReportResponse visits = analyticsService.getBranchReport(
                branchId, menuId, ownerId, from, to);

        AnalyticsDtos.OrdersAnalytics orders = aggregates.buildOrders(menuIds, from, to);
        AnalyticsDtos.TablesAnalytics tables = aggregates.buildTables(menuIds, from, to);
        long openBills = aggregates.countOpenBills(menuIds);
        long activeTables = aggregates.countActiveTables(menuIds);

        AnalyticsDtos.SummaryOverview overview = buildOverview(
                revenue, previousRevenue, orders, openBills, activeTables);

        Double unassignedShare = unassignedRevenueShare(waiter);

        return new AnalyticsDtos.SummaryAnalyticsResponse(
                revenue.menuId(),
                revenue.menuName(),
                revenue.branchId(),
                revenue.branchName(),
                period,
                overview,
                new AnalyticsDtos.SalesTeaser(
                        revenue.hourly() == null ? List.of() : revenue.hourly(),
                        limitProducts(revenue.products(), TEASER_LIMIT),
                        limitCategories(revenue.categories(), TEASER_LIMIT)
                ),
                new AnalyticsDtos.StaffTeaser(
                        limitWaiters(waiter.waiters(), TEASER_LIMIT),
                        unassignedShare
                ),
                new AnalyticsDtos.OperationsTeaser(openBills, orders.completedOrders()),
                new AnalyticsDtos.TablesTeaser(
                        aggregates.topTables(tables, aggregates.topTablesLimit()),
                        tables.avgDwellMinutes()
                ),
                new AnalyticsDtos.QrTeaser(
                        visits.kpis().sessions(),
                        visits.kpis().menuOpens(),
                        visits.kpis().productViews(),
                        visits.funnel()
                ),
                AnalyticsDtos.AnalyticsCoverage.current()
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.FullAnalyticsResponse getBranchFull(
            Long branchId,
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        validatePeriod(from, to);
        AnalyticsDtos.AnalyticsPeriod period = buildPeriod(from, to);
        Collection<Long> menuIds = resolveMenuIds(branchId, menuId, ownerId);

        AnalyticsDtos.MenuRevenueReportResponse revenue = analyticsService.getBranchRevenueReport(
                branchId, menuId, ownerId, from, to);
        AnalyticsDtos.MenuRevenueReportResponse previousRevenue = analyticsService.getBranchRevenueReport(
                branchId, menuId, ownerId, period.previousFrom(), period.previousTo());
        AnalyticsDtos.MenuWaiterPerformanceReportResponse waiter = analyticsService.getBranchWaiterPerformanceReport(
                branchId, menuId, ownerId, from, to);
        AnalyticsDtos.MenuAnalyticsReportResponse visits = analyticsService.getBranchReport(
                branchId, menuId, ownerId, from, to);

        AnalyticsDtos.OrdersAnalytics orders = aggregates.buildOrders(menuIds, from, to);
        AnalyticsDtos.TablesAnalytics tables = aggregates.buildTables(menuIds, from, to);
        long openBills = aggregates.countOpenBills(menuIds);
        long activeTables = aggregates.countActiveTables(menuIds);
        AnalyticsDtos.SummaryOverview overview = buildOverview(
                revenue, previousRevenue, orders, openBills, activeTables);

        BigDecimal previousGross = previousGross(previousRevenue);

        List<AnalyticsDtos.StaffRankingRow> ranking = buildRanking(waiter.waiters());

        return new AnalyticsDtos.FullAnalyticsResponse(
                revenue.menuId(),
                revenue.menuName(),
                revenue.branchId(),
                revenue.branchName(),
                period,
                AnalyticsDtos.AnalyticsCoverage.current(),
                new AnalyticsDtos.DashboardAnalytics(
                        overview,
                        revenue.daily() == null ? List.of() : revenue.daily(),
                        revenue.hourly() == null ? List.of() : revenue.hourly(),
                        period,
                        previousGross
                ),
                orders,
                new AnalyticsDtos.StaffAnalytics(
                        waiter.kpis(),
                        waiter.waiters() == null ? List.of() : waiter.waiters(),
                        ranking,
                        waiter.hourly() == null ? List.of() : waiter.hourly(),
                        waiter.products() == null ? List.of() : waiter.products()
                ),
                aggregates.buildProducts(revenue, menuIds, from, to),
                tables,
                new AnalyticsDtos.QrAnalytics(
                        visits.kpis(),
                        visits.funnel(),
                        visits.devices() == null ? List.of() : visits.devices(),
                        visits.topProducts() == null ? List.of() : visits.topProducts(),
                        visits.sampleJourneys() == null ? List.of() : visits.sampleJourneys()
                ),
                aggregates.buildFinance(revenue, menuIds, from, to),
                aggregates.buildCancellations(menuIds, branchId, from, to, orders),
                aggregates.buildKitchen(menuIds, from, to),
                aggregates.buildShifts(branchId, from, to),
                aggregates.buildCustomers(menuIds, from, to),
                aggregates.buildDrillDownHints(menuIds, from, to)
        );
    }

    private Collection<Long> resolveMenuIds(Long branchId, Long menuId, Long ownerId) {
        var branch = branchService.requireOwnedForUser(branchId, ownerId);
        List<Menu> menus = menuRepository.findByBranchIdAndDeletedFalse(branch.getId());
        if (menuId == null) {
            return menus.stream().map(Menu::getMenuId).toList();
        }
        boolean owned = menus.stream().anyMatch(m -> menuId.equals(m.getMenuId()));
        if (!owned) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı");
        }
        return List.of(menuId);
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from ve to zorunludur");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("from, to tarihinden sonra olamaz");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_PERIOD_DAYS) {
            throw new BadRequestException("Analitik dönemi en fazla " + MAX_PERIOD_DAYS + " gün olabilir");
        }
    }

    private AnalyticsDtos.AnalyticsPeriod buildPeriod(LocalDate from, LocalDate to) {
        long spanDays = ChronoUnit.DAYS.between(from, to);
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(spanDays);
        return new AnalyticsDtos.AnalyticsPeriod(from, to, previousFrom, previousTo);
    }

    private AnalyticsDtos.SummaryOverview buildOverview(
            AnalyticsDtos.MenuRevenueReportResponse revenue,
            AnalyticsDtos.MenuRevenueReportResponse previousRevenue,
            AnalyticsDtos.OrdersAnalytics orders,
            long openBills,
            long activeTables
    ) {
        AnalyticsDtos.RevenuePaymentBreakdown pb = revenue.paymentBreakdown();
        BigDecimal gross = pb == null || pb.grossRevenue() == null ? BigDecimal.ZERO : pb.grossRevenue();
        BigDecimal net = pb == null || pb.netRevenue() == null ? BigDecimal.ZERO : pb.netRevenue();
        BigDecimal tip = pb == null || pb.tipRevenue() == null ? BigDecimal.ZERO : pb.tipRevenue();
        String currency = pb != null && pb.currency() != null
                ? pb.currency()
                : (revenue.kpis() != null && revenue.kpis().currency() != null ? revenue.kpis().currency() : "TRY");
        BigDecimal avgBasket = revenue.kpis() != null && revenue.kpis().avgOrderValue() != null
                ? revenue.kpis().avgOrderValue()
                : BigDecimal.ZERO;
        long paymentOrderCount = revenue.kpis() == null || revenue.kpis().orderCount() == 0
                ? orders.completedOrders()
                : revenue.kpis().orderCount();

        BigDecimal previousGross = previousGross(previousRevenue);
        Double vsPrevious = null;
        if (previousGross.compareTo(BigDecimal.ZERO) > 0) {
            vsPrevious = gross.subtract(previousGross)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(previousGross, 2, RoundingMode.HALF_UP)
                    .doubleValue();
        } else if (gross.compareTo(BigDecimal.ZERO) > 0) {
            vsPrevious = 100d;
        }

        return new AnalyticsDtos.SummaryOverview(
                gross,
                net,
                tip,
                paymentOrderCount,
                orders.completedOrders(),
                orders.cancelledOrders(),
                avgBasket,
                openBills,
                aggregates.occupancyRatio(openBills, activeTables),
                orders.cancelRate(),
                vsPrevious,
                currency
        );
    }

    private BigDecimal previousGross(AnalyticsDtos.MenuRevenueReportResponse previousRevenue) {
        if (previousRevenue.paymentBreakdown() != null
                && previousRevenue.paymentBreakdown().grossRevenue() != null) {
            return previousRevenue.paymentBreakdown().grossRevenue();
        }
        if (previousRevenue.kpis() != null && previousRevenue.kpis().totalRevenue() != null) {
            return previousRevenue.kpis().totalRevenue();
        }
        return BigDecimal.ZERO;
    }

    private Double unassignedRevenueShare(AnalyticsDtos.MenuWaiterPerformanceReportResponse waiter) {
        if (waiter.kpis() == null) {
            return null;
        }
        long assigned = waiter.kpis().assignedOrderCount();
        long unassigned = waiter.kpis().unassignedOrderCount();
        long total = assigned + unassigned;
        if (total == 0) {
            return null;
        }
        return Math.round(unassigned * 10000.0 / total) / 100.0;
    }

    private List<AnalyticsDtos.StaffRankingRow> buildRanking(List<AnalyticsDtos.WaiterPerformanceRow> waiters) {
        if (waiters == null || waiters.isEmpty()) {
            return List.of();
        }
        List<AnalyticsDtos.WaiterPerformanceRow> sorted = waiters.stream()
                .sorted(Comparator.comparing(
                        (AnalyticsDtos.WaiterPerformanceRow w) -> w.revenue() == null ? BigDecimal.ZERO : w.revenue()
                ).reversed())
                .toList();
        List<AnalyticsDtos.StaffRankingRow> ranking = new java.util.ArrayList<>();
        int rank = 1;
        for (AnalyticsDtos.WaiterPerformanceRow row : sorted) {
            ranking.add(new AnalyticsDtos.StaffRankingRow(
                    row.waiterId(),
                    row.displayName(),
                    row.revenue() == null ? BigDecimal.ZERO : row.revenue(),
                    row.orderCount(),
                    rank++
            ));
        }
        return ranking;
    }

    private List<AnalyticsDtos.RevenueProduct> limitProducts(
            List<AnalyticsDtos.RevenueProduct> products,
            int limit
    ) {
        if (products == null) {
            return List.of();
        }
        return products.stream().limit(limit).toList();
    }

    private List<AnalyticsDtos.RevenueCategory> limitCategories(
            List<AnalyticsDtos.RevenueCategory> categories,
            int limit
    ) {
        if (categories == null) {
            return List.of();
        }
        return categories.stream().limit(limit).toList();
    }

    private List<AnalyticsDtos.WaiterPerformanceRow> limitWaiters(
            List<AnalyticsDtos.WaiterPerformanceRow> waiters,
            int limit
    ) {
        if (waiters == null) {
            return List.of();
        }
        return waiters.stream()
                .sorted(Comparator.comparing(
                        (AnalyticsDtos.WaiterPerformanceRow w) -> w.revenue() == null ? BigDecimal.ZERO : w.revenue()
                ).reversed())
                .limit(limit)
                .toList();
    }
}
