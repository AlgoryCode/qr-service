package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
            FunnelCounts funnel
    ) {
    }
}
