package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class SessionAccessPolicy {

    public static final int ACCOUNT_ONBOARDING_DAYS = 15;

    public AccessSession decide(
            LocalDateTime userCreatedAt,
            Optional<TrialLog> trialLog,
            Optional<Purchase> paid,
            LocalDateTime now
    ) {
        if (paid.isPresent()) {
            Purchase purchase = paid.get();
            if (isInDebt(purchase)) {
                return AccessSession.of(
                        AccessDecision.REQUIRE_PAYMENT,
                        purchase.getPackageCode(),
                        purchase.getExpiresAt(),
                        debtDueAt(purchase)
                );
            }
            if (purchase.isUsable()) {
                return AccessSession.of(
                        AccessDecision.ALLOW,
                        purchase.getPackageCode(),
                        purchase.getExpiresAt(),
                        null
                );
            }
        }

        TrialLog log = trialLog.orElse(null);
        if (log != null && log.isActiveAt(now)) {
            return AccessSession.of(
                    AccessDecision.ALLOW,
                    log.getPackageCode(),
                    log.getEndsAt(),
                    null
            );
        }
        if (log == null && withinOnboardingWindow(userCreatedAt, now)) {
            return AccessSession.of(AccessDecision.START_PACKAGE, null, null, null);
        }
        return AccessSession.of(
                AccessDecision.REQUIRE_PURCHASE,
                null,
                log == null ? null : log.getEndsAt(),
                null
        );
    }

    public boolean withinOnboardingWindow(LocalDateTime userCreatedAt, LocalDateTime now) {
        if (userCreatedAt == null || now == null) {
            return false;
        }
        return userCreatedAt.plusDays(ACCOUNT_ONBOARDING_DAYS).isAfter(now);
    }

    public boolean isInDebt(Purchase purchase) {
        SubscriptionStatus status = purchase.getSubscriptionStatus();
        return status == SubscriptionStatus.PAST_DUE || status == SubscriptionStatus.GRACE_PERIOD;
    }

    private LocalDateTime debtDueAt(Purchase purchase) {
        if (purchase.getSubscriptionGraceEndsAt() != null) {
            return purchase.getSubscriptionGraceEndsAt();
        }
        return purchase.getExpiresAt();
    }
}
