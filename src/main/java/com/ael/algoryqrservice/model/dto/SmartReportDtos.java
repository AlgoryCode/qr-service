package com.ael.algoryqrservice.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

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
            LocalDateTime createdAt
    ) {
    }

    public record SmartReportQuotaResponse(
            String period,
            int limit,
            long used,
            long remaining,
            Instant resetsAt,
            Instant lastUsage
    ) {
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
