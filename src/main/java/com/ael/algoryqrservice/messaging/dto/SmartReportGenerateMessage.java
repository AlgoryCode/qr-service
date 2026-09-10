package com.ael.algoryqrservice.messaging.dto;

import com.ael.algoryqrservice.model.dto.SmartReportModelDtos;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmartReportGenerateMessage(
        UUID jobId,
        Long userId,
        Long menuId,
        Long branchId,
        SmartReportModelDtos.SmartReportModelInput input,
        String locale
) {
}
