package com.ael.algoryqrservice.qrservice.analytics.assistant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AssistantReportDtos {

    private AssistantReportDtos() {
    }

    /** Sent by the dashboard after each assistant question. Carries no message text. */
    public record RecordUsageRequest(
            @Positive Long branchId,
            @NotNull AssistantUsageTopic topic,
            @NotNull AssistantUsageOutcome outcome,
            @Min(0) @Max(600_000) Integer responseMs
    ) {
    }

    /** Dashboard assistant questions the merchant got answered today, across all branches. */
    public record TodayUsageResponse(LocalDate date, long answered) {
    }

    public record AssistantReportResponse(
            Long branchId,
            String branchName,
            LocalDate from,
            LocalDate to,
            String currency,
            MenuAssistantReport menu,
            DashboardAssistantReport dashboard
    ) {
    }

    /** What the guest-facing menu assistant did for sales in the period. */
    public record MenuAssistantReport(
            long chatSessions,
            long chatOpens,
            long recommendationRequests,
            long recommendationsShown,
            long productClicks,
            long productAdds,
            long crossSellShown,
            long crossSellAdds,
            long ordersWithAssistant,
            long assistantUnits,
            BigDecimal assistantRevenue,
            long orderCount,
            BigDecimal orderRevenue,
            List<DailyRevenue> daily,
            List<ProductRevenue> topProducts
    ) {
    }

    /** How much the merchant leaned on the dashboard assistant in the period. */
    public record DashboardAssistantReport(
            long questions,
            long answered,
            long failed,
            long activeDays,
            long estimatedMinutesSaved,
            Integer averageResponseMs,
            List<TopicUsage> topics,
            List<DailyUsage> daily
    ) {
    }

    public record DailyRevenue(LocalDate date, long units, BigDecimal revenue) {
    }

    public record ProductRevenue(Long productId, String productName, long units, BigDecimal revenue) {
    }

    public record TopicUsage(AssistantUsageTopic topic, long questions, long minutesSaved) {
    }

    public record DailyUsage(LocalDate date, long questions, long minutesSaved) {
    }
}
