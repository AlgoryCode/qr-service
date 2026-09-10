package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class AnalyticsDtos {

    public record VisitSummaryResponse(
            long totalVisits,
            long uniqueIpCount,
            long mobileCount,
            long tabletCount,
            long desktopCount
    ) {
    }

    public record DailyVisitResponse(
            LocalDate date,
            long count
    ) {
    }

    public record VisitPageResponse(
            VisitSummaryResponse summary,
            List<DailyVisitResponse> daily
    ) {
    }

    public record AnalyticsEventItemRequest(
            @NotNull MenuAnalyticsEventType type,
            Long categoryId,
            Long productId,
            Integer servesPeople,
            Integer sequence,
            LocalDateTime occurredAt
    ) {
    }

    public record AnalyticsEventsRequest(
            @NotNull UUID sessionId,
            @Size(max = 16) String deviceType,
            @NotEmpty @Size(max = 50) @Valid List<AnalyticsEventItemRequest> events
    ) {
    }

    public record ReportKpis(
            long sessions,
            long menuOpens,
            long productViews,
            long categoryViews,
            double avgProductsPerSession
    ) {
    }

    public record DailyReportPoint(
            LocalDate date,
            long sessions,
            long menuOpens,
            long productViews
    ) {
    }

    public record HourlyReportPoint(
            int hour,
            long views
    ) {
    }

    public record NamedCount(
            String name,
            long value
    ) {
    }

    public record TopProduct(
            Long productId,
            String name,
            long views
    ) {
    }

    public record TopCategory(
            Long categoryId,
            String name,
            long views
    ) {
    }

    public record TreemapNode(
            String name,
            long size,
            List<TreemapNode> children
    ) {
    }

    public record JourneyStep(
            String type,
            String name,
            LocalDateTime at
    ) {
    }

    public record SampleJourney(
            UUID sessionId,
            LocalDateTime startedAt,
            List<JourneyStep> steps
    ) {
    }

    public record FunnelCounts(
            long menuOpens,
            long categoryViews,
            long productViews,
            long addToCart,
            long checkoutStarts,
            long orderSubmitted
    ) {
        public FunnelCounts(long menuOpens, long categoryViews, long productViews) {
            this(menuOpens, categoryViews, productViews, 0L, 0L, 0L);
        }
    }

    public record ScoreHistogramBucket(
            int score,
            long count
    ) {
    }

    public record FeedbackCommentSample(
            Long productId,
            String productName,
            int score,
            String comment,
            LocalDateTime createdAt
    ) {
    }

    public record RatedProductSummary(
            Long productId,
            String name,
            BigDecimal ratingAvg,
            long ratingCount
    ) {
    }

    public record MenuFeedbackSummary(
            BigDecimal ratingAvg,
            long ratingCount,
            List<ScoreHistogramBucket> scoreHistogram,
            List<FeedbackCommentSample> sampleComments
    ) {
    }

    public record ProductFeedbackSummary(
            BigDecimal ratingAvg,
            long ratingCount,
            List<RatedProductSummary> topRated,
            List<RatedProductSummary> bottomRated,
            List<ScoreHistogramBucket> scoreHistogram,
            List<FeedbackCommentSample> sampleComments
    ) {
    }

    public record ReportFeedback(
            MenuFeedbackSummary menu,
            ProductFeedbackSummary products
    ) {
    }

    public record MenuAnalyticsReportResponse(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
            LocalDate from,
            LocalDate to,
            ReportKpis kpis,
            List<DailyReportPoint> daily,
            List<HourlyReportPoint> hourly,
            List<NamedCount> devices,
            List<TopProduct> topProducts,
            List<TopCategory> topCategories,
            List<TreemapNode> categoryProductTree,
            List<SampleJourney> sampleJourneys,
            FunnelCounts funnel,
            ReportFeedback feedback
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

    public record DailyRevenuePoint(
            LocalDate date,
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

    public record HourlyRevenuePoint(
            int hour,
            BigDecimal revenue,
            long orderCount
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

    public record MenuRevenueReportResponse(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
            LocalDate from,
            LocalDate to,
            RevenueKpis kpis,
            List<DailyRevenuePoint> daily,
            List<RevenueProduct> products,
            List<RevenueCategory> categories,
            RevenueSpotlight spotlight,
            List<HourlyRevenuePoint> hourly,
            UnsoldCatalog unsold,
            RevenuePaymentBreakdown paymentBreakdown,
            List<RevenuePersonnelRow> personnel,
            List<ChannelShare> channels,
            List<ChannelDailyPoint> channelDaily
    ) {
    }

    public record RevenuePaymentBreakdown(
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

    public record ChannelDailyPoint(
            LocalDate date,
            String channelCode,
            BigDecimal revenue,
            long orderCount
    ) {
    }

    public record RevenuePersonnelRow(
            Long waiterId,
            String displayName,
            BigDecimal revenue,
            BigDecimal cashRevenue,
            BigDecimal cardRevenue,
            BigDecimal tipRevenue,
            boolean active
    ) {
    }

    public record WaiterPerformanceProduct(
            Long productId,
            String name,
            long quantity,
            BigDecimal revenue
    ) {
    }

    public record WaiterPerformanceKpis(
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

    public record WaiterPerformanceRow(
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
            List<WaiterPerformanceProduct> topProducts
    ) {
    }

    public record MenuWaiterPerformanceReportResponse(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
            LocalDate from,
            LocalDate to,
            WaiterPerformanceKpis kpis,
            List<WaiterPerformanceRow> waiters,
            List<DailyRevenuePoint> daily,
            List<HourlyRevenuePoint> hourly,
            List<WaiterPerformanceProduct> products
    ) {
    }

    public record AnalyticsCoverage(
            boolean kitchenMetrics,
            boolean discounts,
            boolean shifts,
            boolean cartToOrderFunnel,
            boolean coversPerTable
    ) {
        public static AnalyticsCoverage current() {
            return new AnalyticsCoverage(true, true, true, true, true);
        }
    }

    public record AnalyticsPeriod(
            LocalDate from,
            LocalDate to,
            LocalDate previousFrom,
            LocalDate previousTo
    ) {
    }

    public record SummaryOverview(
            BigDecimal grossRevenue,
            BigDecimal netRevenue,
            BigDecimal tipTotal,
            long orderCount,
            long completedOrderCount,
            long cancelledOrderCount,
            BigDecimal avgBasket,
            long activeOpenTables,
            Double occupancyRatio,
            double cancelRate,
            Double revenueVsPreviousPct,
            String currency
    ) {
    }

    public record SalesTeaser(
            List<HourlyRevenuePoint> hourlyRevenue,
            List<RevenueProduct> topProducts,
            List<RevenueCategory> categoryShare
    ) {
    }

    public record StaffTeaser(
            List<WaiterPerformanceRow> topWaitersByRevenue,
            Double unassignedRevenueShare
    ) {
    }

    public record OperationsTeaser(
            long openBills,
            long confirmedOrders
    ) {
    }

    public record TablesTeaser(
            List<TableAnalyticsRow> topTablesByRevenue,
            Double avgTurnMinutes
    ) {
    }

    public record QrTeaser(
            long sessions,
            long menuOpens,
            long productViews,
            FunnelCounts browseFunnel
    ) {
    }

    public record SummaryAnalyticsResponse(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
            AnalyticsPeriod period,
            SummaryOverview overview,
            SalesTeaser salesTeaser,
            StaffTeaser staffTeaser,
            OperationsTeaser operationsTeaser,
            TablesTeaser tablesTeaser,
            QrTeaser qrTeaser,
            AnalyticsCoverage coverage
    ) {
    }

    public record StatusCount(
            String status,
            long count
    ) {
    }

    public record HourlyOrderPoint(
            int hour,
            long orderCount
    ) {
    }

    public record DailyOrderPoint(
            LocalDate date,
            long orderCount
    ) {
    }

    public record OrdersAnalytics(
            List<StatusCount> countsByStatus,
            List<HourlyOrderPoint> hourly,
            List<DailyOrderPoint> daily,
            double avgItemsPerOrder,
            double cancelRate,
            BigDecimal aov,
            long totalOrders,
            long completedOrders,
            long cancelledOrders
    ) {
    }

    public record StaffRankingRow(
            Long waiterId,
            String displayName,
            BigDecimal revenue,
            long orderCount,
            int rank
    ) {
    }

    public record StaffAnalytics(
            WaiterPerformanceKpis kpis,
            List<WaiterPerformanceRow> rows,
            List<StaffRankingRow> ranking,
            List<HourlyRevenuePoint> hourly,
            List<WaiterPerformanceProduct> products
    ) {
    }

    public record ProductCoPurchaseCompanion(
            Long productId,
            String name,
            long togetherCount,
            double sharePercent
    ) {
    }

    public record ProductCoPurchase(
            Long productId,
            String name,
            long billCount,
            List<ProductCoPurchaseCompanion> companions
    ) {
    }

    public record ProductsAnalytics(
            List<RevenueProduct> top,
            List<RevenueProduct> bottom,
            List<RevenueCategory> byCategory,
            List<HourlyRevenuePoint> hourly,
            List<ProductCoPurchase> coPurchase,
            UnsoldCatalog unsold
    ) {
    }

    public record TableAnalyticsRow(
            Long tableId,
            String tableName,
            BigDecimal revenue,
            long orderCount,
            long billCount,
            BigDecimal avgCheck,
            Double avgDwellMinutes,
            long closedBills,
            long qrOrderCount,
            long staffOrderCount,
            Integer capacity,
            Integer coverCountTotal,
            BigDecimal revenuePerCover
    ) {
    }

    public record TablesAnalytics(
            List<TableAnalyticsRow> perTable,
            Double avgDwellMinutes,
            Double turnRate,
            long qrOrderCount,
            long staffOrderCount,
            Double qrOrderSharePercent,
            Double occupancyRatio,
            BigDecimal avgRevenuePerCover
    ) {
    }

    public record QrAnalytics(
            ReportKpis kpis,
            FunnelCounts funnel,
            List<NamedCount> devices,
            List<TopProduct> topViewed,
            List<SampleJourney> journeys
    ) {
    }

    public record FinanceAnalytics(
            BigDecimal grossRevenue,
            BigDecimal tipRevenue,
            BigDecimal cashRevenue,
            BigDecimal cardRevenue,
            BigDecimal fixedExpenses,
            BigDecimal netRevenue,
            BigDecimal orderTotalAmount,
            BigDecimal collectedAmount,
            BigDecimal discountTotal,
            BigDecimal refundTotal,
            BigDecimal voidTotal,
            List<ChannelShare> channels,
            RevenuePaymentBreakdown paymentBreakdown,
            String currency
    ) {
    }

    public record CancellationByWaiter(
            Long waiterId,
            String displayName,
            long cancelCount
    ) {
    }

    public record CancellationsAnalytics(
            List<StatusCount> byStatus,
            List<CancellationByWaiter> byWaiter,
            long totalCancelled,
            long totalRejected
    ) {
    }

    public record CustomersAnalytics(
            long identifiedCustomerOrderCount,
            long distinctIdentifiedCustomers,
            long anonymousSessions,
            long repeatAnonymousSessions,
            long newAnonymousVisitors,
            long returningAnonymousVisitors,
            Double identifiedOrderSharePercent
    ) {
    }

    public record DrillDownLink(
            Long billId,
            Long orderId,
            Long tableId,
            Long waiterId,
            Long productId,
            LocalDateTime paidAt,
            BigDecimal amount
    ) {
    }

    public record DashboardAnalytics(
            SummaryOverview overview,
            List<DailyRevenuePoint> daily,
            List<HourlyRevenuePoint> hourly,
            AnalyticsPeriod previousPeriod,
            BigDecimal previousGrossRevenue
    ) {
    }

    public record KitchenStageKpis(
            Double avgConfirmToPrepareMinutes,
            Double avgPrepareToReadyMinutes,
            Double avgReadyToServeMinutes,
            Double avgConfirmToServeMinutes,
            long preparingCount,
            long readyCount,
            long servedCount,
            long delayedCount,
            double delayRatePercent
    ) {
    }

    public record KitchenHourlyLoad(
            int hour,
            long orderCount,
            Double avgPrepMinutes
    ) {
    }

    public record KitchenAnalytics(
            KitchenStageKpis kpis,
            List<KitchenHourlyLoad> hourlyLoad,
            long delayThresholdMinutes
    ) {
    }

    public record ShiftSummaryRow(
            Long shiftId,
            LocalDateTime openedAt,
            LocalDateTime closedAt,
            String status,
            BigDecimal openingFloat,
            BigDecimal closingCash,
            BigDecimal revenue,
            long orderCount,
            long waiterCount
    ) {
    }

    public record ShiftsAnalytics(
            List<ShiftSummaryRow> shifts,
            BigDecimal totalRevenue,
            long totalOrders
    ) {
    }

    public record FullAnalyticsResponse(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
            AnalyticsPeriod period,
            AnalyticsCoverage coverage,
            DashboardAnalytics dashboard,
            OrdersAnalytics orders,
            StaffAnalytics staff,
            ProductsAnalytics products,
            TablesAnalytics tables,
            QrAnalytics qr,
            FinanceAnalytics finance,
            CancellationsAnalytics cancellations,
            KitchenAnalytics kitchen,
            ShiftsAnalytics shifts,
            CustomersAnalytics customers,
            List<DrillDownLink> drillDownHints
    ) {
    }
}
