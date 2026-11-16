package com.ael.algoryqrservice.qrservice.account.dto;

import com.ael.algoryqrservice.qrservice.access.BillingState;
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
        /** NONE, TRIALING, ACTIVE, PAST_DUE, FREE or CLOSED; see BillingState. */
        BillingState billingState,
        List<String> products,
        List<String> scopes,
        List<SessionEntitlementResponse> entitlements
) {
}
