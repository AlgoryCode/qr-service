package com.ael.algoryqrservice.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BatchReportDtos {

    private BatchReportDtos() {
    }

    public record RequestCounts(
            int total,
            int completed,
            int failed
    ) {
    }

    public record BatchReportListItem(
            UUID id,
            String openaiBatchId,
            String status,
            RequestCounts requestCounts,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
    }

    public record BatchReportItemDetail(
            UUID id,
            String status,
            Long branchId,
            String branchName,
            Long menuId,
            String menuName,
            LocalDate from,
            LocalDate to,
            String locale,
            Map<String, Object> result,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record BatchReportDetail(
            UUID id,
            String openaiBatchId,
            String status,
            RequestCounts requestCounts,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime completedAt,
            List<BatchReportItemDetail> items
    ) {
    }
}
