package com.ael.algoryqrservice.qrservice.messaging.dto;

import com.ael.algoryqrservice.qrservice.analytics.dto.SmartReportModelDtos;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmartReportGenerateMessage(
        UUID jobId,
        Long merchantId,
        Long menuId,
        Long branchId,
        SmartReportModelDtos.SmartReportModelInput input,
        String locale
) {
}
