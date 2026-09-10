package com.ael.algoryqrservice.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SmartReportModelDtos {

    public static final int TOP_PRODUCTS_LIMIT = 15;
    public static final int TOP_CATEGORIES_LIMIT = 10;
    public static final int UNSOLD_LIMIT = 20;
    public static final int WAITERS_LIMIT = 20;
    public static final int WAITER_TOP_PRODUCTS_LIMIT = 5;

    private SmartReportModelDtos() {
    }

    public record SmartReportModelInput(
            Meta meta,
            RevenueBlock revenue,
            WaiterBlock waiter,
            VisitsBlock visits,
            OptionsBlock options
    ) {
    }

    public record Meta(
            Long branchId,
            String branchName,
            LocalDate from,
            LocalDate to,
            String locale,
            String currency
    ) {
    }

    public record OptionsBlock(
            String tone,
            Integer maxLength,
            List<String> focusAreas
    ) {
    }

    public record RevenueBlock(
            RevenueKpis kpis,
            PaymentBreakdown paymentBreakdown,
            List<ChannelShare> channels,
            List<DailyRevenuePoint> daily,
            List<HourlyRevenuePoint> hourly,
            List<RevenueProduct> products,
            List<RevenueCategory> categories,
            RevenueSpotlight spotlight,
            UnsoldCatalog unsold
    ) {
    }

    public record RevenueKpis(
            BigDecimal totalRevenue,
            long orderCount,
            long itemCount,
            BigDecimal avgOrderValue,
            String currency
    ) {
    }

    public record PaymentBreakdown(
            BigDecimal cashRevenue,
            BigDecimal cardRevenue,
            BigDecimal tipRevenue,
            BigDecimal uberEatsRevenue,
            BigDecimal grossRevenue,
            BigDecimal fixedExpenseTotal,
            BigDecimal netRevenue,
            String currency
    ) {
    }

    public record ChannelShare(
            String code,
            String label,
            BigDecimal revenue,
            long orderCount,
            BigDecimal avgOrderValue,
            BigDecimal sharePercent,
            BigDecimal deltaVsPrevPercent,
            boolean connected
    ) {
    }

    public record DailyRevenuePoint(
            LocalDate date,
            BigDecimal revenue,
            long orderCount
    ) {
    }

    public record HourlyRevenuePoint(
            int hour,
            BigDecimal revenue,
            long orderCount
    ) {
    }

    public record RevenueProduct(
            Long productId,
            String name,
            long quantity,
            BigDecimal revenue
    ) {
    }

    public record RevenueCategory(
            Long categoryId,
            String name,
            long quantity,
            BigDecimal revenue
    ) {
    }

    public record RevenueSpotlightProduct(
            Long productId,
            String name,
            long quantity,
            BigDecimal revenue
    ) {
    }

    public record RevenueSpotlight(
            RevenueSpotlightProduct byQuantity,
            RevenueSpotlightProduct byRevenue,
            RevenueSpotlightProduct leastSoldByQuantity
    ) {
    }

    public record UnsoldProduct(
            Long productId,
            String name
    ) {
    }

    public record UnsoldCatalog(
            long count,
            List<UnsoldProduct> products
    ) {
    }

    public record WaiterBlock(
            WaiterKpis kpis,
            List<WaiterRow> waiters
    ) {
    }

    public record WaiterKpis(
            long activeWaiterCount,
            long assignedOrderCount,
            long unassignedOrderCount,
            BigDecimal totalRevenue,
            long itemCount,
            BigDecimal totalCommission,
            BigDecimal totalTip,
            long billsClosedCount,
            String currency
    ) {
    }

    public record WaiterRow(
            Long waiterId,
            String displayName,
            long orderCount,
            long itemCount,
            BigDecimal revenue,
            BigDecimal tipAmount,
            BigDecimal commissionAmount,
            long billsClosedCount,
            BigDecimal avgOrderValue,
            double revenueSharePercent,
            double orderSharePercent,
            double itemSharePercent,
            boolean active,
            List<WaiterProduct> topProducts
    ) {
    }

    public record WaiterProduct(
            Long productId,
            String name,
            long quantity,
            BigDecimal revenue
    ) {
    }

    public record VisitsBlock(
            VisitKpis kpis,
            FunnelCounts funnel,
            List<DailyVisitPoint> daily,
            List<HourlyVisitPoint> hourly
    ) {
    }

    public record VisitKpis(
            long sessions,
            long menuOpens,
            long productViews,
            long categoryViews,
            double avgProductsPerSession
    ) {
    }

    public record FunnelCounts(
            long menuOpens,
            long categoryViews,
            long productViews
    ) {
    }

    public record DailyVisitPoint(
            LocalDate date,
            long sessions,
            long menuOpens,
            long productViews
    ) {
    }

    public record HourlyVisitPoint(
            int hour,
            long views
    ) {
    }
}
