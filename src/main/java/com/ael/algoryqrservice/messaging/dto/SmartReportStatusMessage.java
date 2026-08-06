package com.ael.algoryqrservice.messaging.dto;

import com.ael.algoryqrservice.model.dto.SmartReportDtos;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartReportStatusMessage(
        @JsonAlias({"job_id", "process_id", "processId"}) UUID jobId,
        String status,
        SmartReportDtos.AiSmartReportResult result,
        @JsonAlias({"result_text", "resultText"}) String resultText,
        @JsonAlias({"error_code"}) String errorCode,
        @JsonAlias({"error_message"}) String errorMessage,
        @JsonAlias({"updated_at"}) Instant updatedAt,
        @JsonAlias({"completed_at"}) Instant completedAt
) {
}
