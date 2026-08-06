package com.ael.algoryqrservice.messaging.dto;

import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmartReportGenerateMessage(
        UUID jobId,
        Long userId,
        Long menuId,
        AnalyticsDtos.MenuAnalyticsReportResponse report,
        String locale,
        Map<String, Object> options
) {
}
