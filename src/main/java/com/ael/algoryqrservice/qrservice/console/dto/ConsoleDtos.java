package com.ael.algoryqrservice.qrservice.console.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ConsoleDtos {

    private ConsoleDtos() {
    }

    /** {@code hourlyRate} null means the console uses the branch default. */
    public record ConsoleRequest(
            @NotBlank @Size(max = 120) String name,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal hourlyRate
    ) {
    }

    public record ConsoleResponse(
            Long id,
            Long branchId,
            String name,
            BigDecimal hourlyRate,
            BigDecimal effectiveHourlyRate,
            boolean busy
    ) {
    }

    public record RateSettingRequest(
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal defaultHourlyRate
    ) {
    }

    public record RateSettingResponse(
            Long branchId,
            BigDecimal defaultHourlyRate
    ) {
    }

    public record StartSessionRequest(
            @Size(max = 120) String customerName,
            @Size(max = 500) String note
    ) {
    }

    /**
     * For an open session {@code durationMinutes} and {@code amount} are the running totals up to now;
     * once it is finished they are the stored, final values.
     */
    public record SessionResponse(
            Long id,
            Long branchId,
            Long consoleId,
            String consoleName,
            String customerName,
            String note,
            BigDecimal hourlyRate,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            int durationMinutes,
            BigDecimal amount,
            boolean active
    ) {
    }

    public record SessionPageResponse(
            List<SessionResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
