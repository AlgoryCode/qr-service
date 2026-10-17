package com.ael.algoryqrservice.qrservice.model.dto;

import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SessionContextResponse(
        SessionUserResponse user,
        AccessDecision decision,
        String packageCode,
        String packageName,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDateTime endsAt,
        LocalDateTime debtDueAt,
        String messageKey,
        List<String> products,
        List<String> scopes,
        List<SessionEntitlementResponse> entitlements
) {
}
