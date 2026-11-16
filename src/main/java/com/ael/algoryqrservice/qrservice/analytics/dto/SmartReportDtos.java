package com.ael.algoryqrservice.qrservice.analytics.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SmartReportDtos {

    private SmartReportDtos() {
    }

    public record SmartReportCreateRequest(
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            String locale,
            Options options
    ) {
    }

    public record Options(
            String tone,
            Integer maxLength,
            List<String> focusAreas
    ) {
    }

    /**
     * Answers to the questions under a finished report. Every field is optional.
     *
     * @param helpful            YES, PARTLY or NO
     * @param recommendationsFit FITS, PARTLY or NOT_FIT
     * @param detailLevel        SHORTER, SAME or LONGER
     */
    public record FeedbackRequest(
            String helpful,
            String recommendationsFit,
            List<String> unclearSections,
            List<String> focusAreas,
            String detailLevel,
            String note
    ) {
    }

    public record FeedbackResponse(
            UUID jobId,
            String helpful,
            String recommendationsFit,
            List<String> unclearSections,
            List<String> focusAreas,
            String detailLevel,
            String note,
            UUID revisionJobId,
            LocalDateTime updatedAt,
            /** True when this feedback earned the weekly extra report credit. */
            boolean bonusGranted
    ) {
    }

    public record SmartReportAccepted(
            UUID jobId,
            String status
    ) {
    }

    public record SmartReportListItem(
            UUID jobId,
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
            LocalDate from,
            LocalDate to,
            String locale,
            String status,
            LocalDateTime createdAt,
            /** FREE, BONUS or PAID: which credit paid for the report. */
            String creditSource
    ) {
    }

    /** Reports created per day over the last N days, split by the credit that paid for them. */
    public record SmartReportUsageResponse(
            int days,
            LocalDate from,
            LocalDate to,
            UsageTotals totals,
            List<UsageDay> daily,
            List<SmartReportListItem> items
    ) {
    }

    public record UsageTotals(long free, long bonus, long paid, long total) {
    }

    public record UsageDay(LocalDate date, long free, long bonus, long paid) {
    }

    public record SmartReportQuotaResponse(
            String period,
            int limit,
            long used,
            long remaining,
            Instant resetsAt,
            Instant lastUsage,
            int paidCredits,
            String addonProductCode,
            BigDecimal addonUnitPrice,
            /** Extra credit earned by giving feedback this week and not yet spent (0 or 1); included in limit/remaining. */
            int bonusCredits,
            /** True while feedback can still earn this week's extra credit. */
            boolean feedbackBonusAvailable
    ) {
        public SmartReportQuotaResponse(
                String period,
                int limit,
                long used,
                long remaining,
                Instant resetsAt,
                Instant lastUsage
        ) {
            this(period, limit, used, remaining, resetsAt, lastUsage, 0, null, null, 0, false);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiSmartReportResult(
            String title,
            String summary,
            List<AiReportSection> sections,
            @JsonAlias({"raw_markdown"}) String rawMarkdown,
            String model,
            @JsonAlias({"prompt_version"}) String promptVersion,
            AiTokenUsage usage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiReportSection(
            String key,
            String heading,
            String body
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiTokenUsage(
            @JsonAlias({"input_tokens"}) Integer inputTokens,
            @JsonAlias({"output_tokens"}) Integer outputTokens,
            @JsonAlias({"total_tokens"}) Integer totalTokens
    ) {
    }

    public record SmartReportDetailResponse(
            UUID jobId,
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
            LocalDate from,
            LocalDate to,
            String locale,
            LocalDateTime requestedAt,
            String status,
            AiSmartReportResult result,
            String resultText,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {
    }

    public static Map<String, Object> toOptionsMap(Options options) {
        if (options == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        if (options.tone() != null) {
            map.put("tone", options.tone());
        }
        if (options.maxLength() != null) {
            map.put("maxLength", options.maxLength());
        }
        if (options.focusAreas() != null) {
            map.put("focusAreas", options.focusAreas());
        }
        return map.isEmpty() ? null : map;
    }
}
