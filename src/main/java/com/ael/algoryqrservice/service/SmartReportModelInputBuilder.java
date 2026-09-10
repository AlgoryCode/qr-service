package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.dto.SmartReportModelDtos;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class SmartReportModelInputBuilder {

    public SmartReportModelDtos.SmartReportModelInput build(
            AnalyticsDtos.MenuAnalyticsReportResponse visits,
            AnalyticsDtos.MenuRevenueReportResponse revenue,
            AnalyticsDtos.MenuWaiterPerformanceReportResponse waiter,
            String locale,
            Map<String, Object> optionsMap
    ) {
        String currency = resolveCurrency(revenue);
        SmartReportModelDtos.Meta meta = new SmartReportModelDtos.Meta(
                revenue.branchId() != null ? revenue.branchId() : (visits != null ? visits.branchId() : null),
                revenue.branchName() != null ? revenue.branchName() : (visits != null ? visits.branchName() : null),
                revenue.from(),
                revenue.to(),
                locale,
                currency
        );
        return new SmartReportModelDtos.SmartReportModelInput(
                meta,
                mapRevenue(revenue),
                mapWaiter(waiter),
                mapVisits(visits),
                mapOptions(optionsMap)
        );
    }

    private static String resolveCurrency(AnalyticsDtos.MenuRevenueReportResponse revenue) {
        if (revenue.kpis() != null && revenue.kpis().currency() != null) {
            return revenue.kpis().currency();
        }
        if (revenue.paymentBreakdown() != null && revenue.paymentBreakdown().currency() != null) {
            return revenue.paymentBreakdown().currency();
        }
        return null;
    }

    private static SmartReportModelDtos.RevenueBlock mapRevenue(AnalyticsDtos.MenuRevenueReportResponse revenue) {
        AnalyticsDtos.RevenueKpis k = revenue.kpis();
        SmartReportModelDtos.RevenueKpis kpis = k == null ? null : new SmartReportModelDtos.RevenueKpis(
                k.totalRevenue(),
                k.orderCount(),
                k.itemCount(),
                k.avgOrderValue(),
                k.currency()
        );
        AnalyticsDtos.RevenuePaymentBreakdown p = revenue.paymentBreakdown();
        SmartReportModelDtos.PaymentBreakdown payment = p == null ? null : new SmartReportModelDtos.PaymentBreakdown(
                p.cashRevenue(),
                p.cardRevenue(),
                p.tipRevenue(),
                p.uberEatsRevenue(),
                p.grossRevenue(),
                p.fixedExpenseTotal(),
                p.netRevenue(),
                p.currency()
        );
        List<SmartReportModelDtos.ChannelShare> channels = revenue.channels() == null
                ? List.of()
                : revenue.channels().stream()
                .map(c -> new SmartReportModelDtos.ChannelShare(
                        c.code(),
                        c.label(),
                        c.revenue(),
                        c.orderCount(),
                        c.avgOrderValue(),
                        c.sharePercent(),
                        c.deltaVsPrevPercent(),
                        c.connected()
                ))
                .toList();
        List<SmartReportModelDtos.DailyRevenuePoint> daily = revenue.daily() == null
                ? List.of()
                : revenue.daily().stream()
                .map(d -> new SmartReportModelDtos.DailyRevenuePoint(d.date(), d.revenue(), d.orderCount()))
                .toList();
        List<SmartReportModelDtos.HourlyRevenuePoint> hourly = revenue.hourly() == null
                ? List.of()
                : revenue.hourly().stream()
                .map(h -> new SmartReportModelDtos.HourlyRevenuePoint(h.hour(), h.revenue(), h.orderCount()))
                .toList();
        List<SmartReportModelDtos.RevenueProduct> products = limit(
                revenue.products() == null ? List.of() : revenue.products().stream()
                        .map(pr -> new SmartReportModelDtos.RevenueProduct(
                                pr.productId(), pr.name(), pr.quantity(), pr.revenue()))
                        .toList(),
                SmartReportModelDtos.TOP_PRODUCTS_LIMIT
        );
        List<SmartReportModelDtos.RevenueCategory> categories = limit(
                revenue.categories() == null ? List.of() : revenue.categories().stream()
                        .map(c -> new SmartReportModelDtos.RevenueCategory(
                                c.categoryId(), c.name(), c.quantity(), c.revenue()))
                        .toList(),
                SmartReportModelDtos.TOP_CATEGORIES_LIMIT
        );
        SmartReportModelDtos.RevenueSpotlight spotlight = mapSpotlight(revenue.spotlight());
        SmartReportModelDtos.UnsoldCatalog unsold = mapUnsold(revenue.unsold());
        return new SmartReportModelDtos.RevenueBlock(
                kpis, payment, channels, daily, hourly, products, categories, spotlight, unsold
        );
    }

    private static SmartReportModelDtos.RevenueSpotlight mapSpotlight(AnalyticsDtos.RevenueSpotlight spotlight) {
        if (spotlight == null) {
            return null;
        }
        return new SmartReportModelDtos.RevenueSpotlight(
                mapSpotlightProduct(spotlight.byQuantity()),
                mapSpotlightProduct(spotlight.byRevenue()),
                mapSpotlightProduct(spotlight.leastSoldByQuantity())
        );
    }

    private static SmartReportModelDtos.RevenueSpotlightProduct mapSpotlightProduct(
            AnalyticsDtos.RevenueSpotlightProduct product
    ) {
        if (product == null) {
            return null;
        }
        return new SmartReportModelDtos.RevenueSpotlightProduct(
                product.productId(), product.name(), product.quantity(), product.revenue()
        );
    }

    private static SmartReportModelDtos.UnsoldCatalog mapUnsold(AnalyticsDtos.UnsoldCatalog unsold) {
        if (unsold == null) {
            return new SmartReportModelDtos.UnsoldCatalog(0, List.of());
        }
        List<SmartReportModelDtos.UnsoldProduct> products = unsold.products() == null
                ? List.of()
                : limit(
                unsold.products().stream()
                        .map(p -> new SmartReportModelDtos.UnsoldProduct(p.productId(), p.name()))
                        .toList(),
                SmartReportModelDtos.UNSOLD_LIMIT
        );
        return new SmartReportModelDtos.UnsoldCatalog(unsold.count(), products);
    }

    private static SmartReportModelDtos.WaiterBlock mapWaiter(
            AnalyticsDtos.MenuWaiterPerformanceReportResponse waiter
    ) {
        if (waiter == null || waiter.kpis() == null) {
            return null;
        }
        AnalyticsDtos.WaiterPerformanceKpis k = waiter.kpis();
        SmartReportModelDtos.WaiterKpis kpis = new SmartReportModelDtos.WaiterKpis(
                k.activeWaiterCount(),
                k.assignedOrderCount(),
                k.unassignedOrderCount(),
                k.totalRevenue(),
                k.itemCount(),
                k.totalCommission(),
                k.totalTip(),
                k.billsClosedCount(),
                k.currency()
        );
        List<SmartReportModelDtos.WaiterRow> waiters = waiter.waiters() == null
                ? List.of()
                : limit(
                waiter.waiters().stream().map(SmartReportModelInputBuilder::mapWaiterRow).toList(),
                SmartReportModelDtos.WAITERS_LIMIT
        );
        return new SmartReportModelDtos.WaiterBlock(kpis, waiters);
    }

    private static SmartReportModelDtos.WaiterRow mapWaiterRow(AnalyticsDtos.WaiterPerformanceRow row) {
        List<SmartReportModelDtos.WaiterProduct> topProducts = row.topProducts() == null
                ? List.of()
                : limit(
                row.topProducts().stream()
                        .map(p -> new SmartReportModelDtos.WaiterProduct(
                                p.productId(), p.name(), p.quantity(), p.revenue()))
                        .toList(),
                SmartReportModelDtos.WAITER_TOP_PRODUCTS_LIMIT
        );
        return new SmartReportModelDtos.WaiterRow(
                row.waiterId(),
                row.displayName(),
                row.orderCount(),
                row.itemCount(),
                row.revenue(),
                row.tipAmount(),
                row.commissionAmount(),
                row.billsClosedCount(),
                row.avgOrderValue(),
                row.revenueSharePercent(),
                row.orderSharePercent(),
                row.itemSharePercent(),
                row.active(),
                topProducts
        );
    }

    private static SmartReportModelDtos.VisitsBlock mapVisits(AnalyticsDtos.MenuAnalyticsReportResponse visits) {
        if (visits == null || visits.kpis() == null) {
            return null;
        }
        AnalyticsDtos.ReportKpis k = visits.kpis();
        SmartReportModelDtos.VisitKpis kpis = new SmartReportModelDtos.VisitKpis(
                k.sessions(),
                k.menuOpens(),
                k.productViews(),
                k.categoryViews(),
                k.avgProductsPerSession()
        );
        AnalyticsDtos.FunnelCounts f = visits.funnel();
        SmartReportModelDtos.FunnelCounts funnel = f == null
                ? new SmartReportModelDtos.FunnelCounts(0, 0, 0)
                : new SmartReportModelDtos.FunnelCounts(f.menuOpens(), f.categoryViews(), f.productViews());
        List<SmartReportModelDtos.DailyVisitPoint> daily = visits.daily() == null
                ? List.of()
                : visits.daily().stream()
                .map(d -> new SmartReportModelDtos.DailyVisitPoint(
                        d.date(), d.sessions(), d.menuOpens(), d.productViews()))
                .toList();
        List<SmartReportModelDtos.HourlyVisitPoint> hourly = visits.hourly() == null
                ? List.of()
                : visits.hourly().stream()
                .map(h -> new SmartReportModelDtos.HourlyVisitPoint(h.hour(), h.views()))
                .toList();
        return new SmartReportModelDtos.VisitsBlock(kpis, funnel, daily, hourly);
    }

    private static SmartReportModelDtos.OptionsBlock mapOptions(Map<String, Object> optionsMap) {
        if (optionsMap == null || optionsMap.isEmpty()) {
            return null;
        }
        String tone = optionsMap.get("tone") instanceof String s ? s : null;
        Integer maxLength = null;
        Object maxRaw = optionsMap.get("maxLength");
        if (maxRaw instanceof Number n) {
            maxLength = n.intValue();
        }
        @SuppressWarnings("unchecked")
        List<String> focusAreas = optionsMap.get("focusAreas") instanceof List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : null;
        if (tone == null && maxLength == null && (focusAreas == null || focusAreas.isEmpty())) {
            return null;
        }
        return new SmartReportModelDtos.OptionsBlock(tone, maxLength, focusAreas);
    }

    private static <T> List<T> limit(List<T> items, int max) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (items.size() <= max) {
            return items;
        }
        return Collections.unmodifiableList(items.subList(0, max));
    }
}
