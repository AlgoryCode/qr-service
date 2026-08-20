package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.model.enums.AccountingLineType;
import com.ael.algoryqrservice.model.enums.AccountingSourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class UserAccountingDtos {

    private UserAccountingDtos() {
    }

    @Getter
    @Setter
    public static class CreateRequest {
        @NotNull
        private AccountingEntryType entryType;

        @NotBlank
        @Size(max = 200)
        private String title;

        @NotNull
        @DecimalMin(value = "0.01")
        private BigDecimal amount;

        @NotNull
        private LocalDateTime occurredAt;

        @Size(max = 500)
        private String note;

        private Long menuId;
    }

    public record LineItemResponse(
            String id,
            AccountingLineType type,
            AccountingEntryType entryType,
            String title,
            BigDecimal amount,
            String currency,
            LocalDateTime occurredAt,
            String note,
            Long billId,
            Long entryId,
            String menuName,
            LocalDateTime createdAt
    ) {
    }

    public record EntryResponse(
            Long id,
            AccountingEntryType entryType,
            String title,
            BigDecimal amount,
            String currency,
            LocalDateTime occurredAt,
            String note,
            Long menuId,
            String menuName,
            AccountingSourceType sourceType,
            Long sourceBillId,
            Long sourceOrderId,
            Long createdByWaiterId,
            LocalDateTime createdAt
    ) {
    }

    public record SummaryTotals(
            BigDecimal totalGelir,
            BigDecimal totalGider,
            BigDecimal totalBorc,
            String currency
    ) {
    }

    public record EntryPageResponse(
            List<LineItemResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            SummaryTotals summary
    ) {
    }
}
