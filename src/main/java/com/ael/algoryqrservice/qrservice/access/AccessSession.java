package com.ael.algoryqrservice.qrservice.access;

import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;

import java.time.LocalDateTime;

/**
 * @param debtDueAt when a failed renewal's grace period ends; null when nothing is owed
 */
public record AccessSession(
        AccessDecision decision,
        String packageCode,
        LocalDateTime endsAt,
        LocalDateTime debtDueAt,
        String messageKey,
        BillingState billingState
) {
    public static AccessSession of(
            AccessDecision decision,
            String packageCode,
            LocalDateTime endsAt,
            LocalDateTime debtDueAt
    ) {
        return new AccessSession(decision, packageCode, endsAt, debtDueAt, messageKeyOf(decision), defaultState(decision));
    }

    public AccessSession withBillingState(BillingState state) {
        return new AccessSession(decision, packageCode, endsAt, debtDueAt, messageKey, state);
    }

    public static String messageKeyOf(AccessDecision decision) {
        return switch (decision) {
            case START_PACKAGE -> "start_package";
            case REQUIRE_PURCHASE -> "require_purchase";
            case REQUIRE_PAYMENT -> "require_payment";
            case ALLOW -> "allow";
        };
    }

    private static BillingState defaultState(AccessDecision decision) {
        return switch (decision) {
            case START_PACKAGE -> BillingState.NONE;
            case REQUIRE_PURCHASE -> BillingState.CLOSED;
            case REQUIRE_PAYMENT -> BillingState.PAST_DUE;
            case ALLOW -> BillingState.ACTIVE;
        };
    }

    public boolean isAllow() {
        return decision == AccessDecision.ALLOW;
    }
}
