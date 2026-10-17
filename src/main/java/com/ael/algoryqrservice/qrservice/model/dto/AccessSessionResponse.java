package com.ael.algoryqrservice.qrservice.model.dto;

import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;

import java.time.LocalDateTime;

public record AccessSessionResponse(
        AccessDecision decision,
        String packageCode,
        LocalDateTime endsAt,
        LocalDateTime debtDueAt,
        String messageKey,
        String code
) {
}
