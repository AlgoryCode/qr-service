package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.AccessDecision;

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
