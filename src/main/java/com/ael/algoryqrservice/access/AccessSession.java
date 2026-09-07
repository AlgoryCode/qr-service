package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.model.enums.AccessDecision;

import java.time.LocalDateTime;

public record AccessSession(
        AccessDecision decision,
        String packageCode,
        LocalDateTime endsAt,
        LocalDateTime debtDueAt,
        String messageKey
) {
    public static AccessSession of(
            AccessDecision decision,
            String packageCode,
            LocalDateTime endsAt,
            LocalDateTime debtDueAt
    ) {
        return new AccessSession(decision, packageCode, endsAt, debtDueAt, messageKeyOf(decision));
    }

    public static String messageKeyOf(AccessDecision decision) {
        return switch (decision) {
            case START_PACKAGE -> "start_package";
            case REQUIRE_PURCHASE -> "require_purchase";
            case REQUIRE_PAYMENT -> "require_payment";
            case ALLOW -> "allow";
        };
    }

    public boolean isAllow() {
        return decision == AccessDecision.ALLOW;
    }
}
