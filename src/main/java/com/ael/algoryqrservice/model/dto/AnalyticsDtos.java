package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
            long productViews
    ) {
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
            @NotBlank String menuName,
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
            List<RevenuePersonnelRow> personnel
    ) {
    }

    public record RevenuePaymentBreakdown(
            BigDecimal cashRevenue,
            BigDecimal cardRevenue,
            BigDecimal tipRevenue,
            BigDecimal grossRevenue,
            BigDecimal fixedExpenseTotal,
            BigDecimal netRevenue,
            String currency
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
            LocalDate from,
            LocalDate to,
            WaiterPerformanceKpis kpis,
            List<WaiterPerformanceRow> waiters,
            List<DailyRevenuePoint> daily,
            List<HourlyRevenuePoint> hourly,
            List<WaiterPerformanceProduct> products
    ) {
    }
}
