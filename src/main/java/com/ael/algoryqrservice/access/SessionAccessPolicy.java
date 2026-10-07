package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class SessionAccessPolicy {

    public AccessSession decide(Optional<Purchase> purchase) {
        if (purchase.isEmpty()) {
            return AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null);
        }
        Purchase active = purchase.get();
        if (isInDebt(active)) {
            return AccessSession.of(
                    AccessDecision.REQUIRE_PAYMENT,
                    active.getPackageCode(),
                    active.getExpiresAt(),
                    debtDueAt(active)
            );
        }
        if (active.isUsable()) {
            return AccessSession.of(
                    AccessDecision.ALLOW,
                    active.getPackageCode(),
                    active.getExpiresAt(),
                    null
            );
        }
        return AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null);
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
