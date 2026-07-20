package com.ael.algoryqrservice.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class TrialDtos {
    private TrialDtos() {
    }

    public enum Lifecycle {
        AVAILABLE,
        ACTIVE,
        TRIAL_EXPIRED
    }

    public record Status(
            Lifecycle lifecycle,
            Long purchaseId,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            Long packageId,
            String packageCode,
            String packageName,
            Integer daysUntilExpiry,
            BigDecimal price,
            String currency
    ) {
    }
}
