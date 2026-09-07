package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SessionAccessPolicyTest {

    private final SessionAccessPolicy policy = new SessionAccessPolicy();
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 7, 10, 0);

    @Test
    void decide_whenYoungAndNoLog_thenStartPackage() {
        AccessSession session = policy.decide(now.minusDays(2), Optional.empty(), Optional.empty(), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.START_PACKAGE);
    }

    @Test
    void decide_whenYoungAndActiveLog_thenAllowOnboardingPackage() {
        TrialLog log = TrialLog.builder()
                .packageCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .status(TrialLogStatus.ACTIVE)
                .endsAt(now.plusDays(10))
                .build();
        AccessSession session = policy.decide(now.minusDays(2), Optional.of(log), Optional.empty(), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(session.packageCode()).isEqualTo(CatalogPackages.ULTIMATE_TRIAL_PACKAGE);
        assertThat(session.endsAt()).isEqualTo(now.plusDays(10));
    }

    @Test
    void decide_whenYoungAndEndedLog_thenRequirePurchase() {
        TrialLog log = TrialLog.builder()
                .status(TrialLogStatus.ENDED)
                .endsAt(now.minusDays(1))
                .build();
        AccessSession session = policy.decide(now.minusDays(10), Optional.of(log), Optional.empty(), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE);
    }

    @Test
    void decide_whenOlderThanFifteenDaysWithoutPaid_thenRequirePurchase() {
        AccessSession session = policy.decide(now.minusDays(20), Optional.empty(), Optional.empty(), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE);
    }

    @Test
    void decide_whenOlderWithActiveLog_thenAllowOnboardingPackage() {
        TrialLog log = TrialLog.builder()
                .packageCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .status(TrialLogStatus.ACTIVE)
                .endsAt(now.plusDays(5))
                .build();
        AccessSession session = policy.decide(now.minusDays(20), Optional.of(log), Optional.empty(), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(session.packageCode()).isEqualTo(CatalogPackages.ULTIMATE_TRIAL_PACKAGE);
    }

    @Test
    void decide_whenOlderWithEndedLog_thenRequirePurchase() {
        TrialLog log = TrialLog.builder()
                .packageCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .status(TrialLogStatus.ENDED)
                .endsAt(now.minusDays(1))
                .build();
        AccessSession session = policy.decide(now.minusDays(20), Optional.of(log), Optional.empty(), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE);
    }

    @Test
    void decide_whenUsablePaid_thenAllowPaid() {
        Purchase paid = paid(SubscriptionStatus.ACTIVE);
        AccessSession session = policy.decide(now.minusDays(2), Optional.empty(), Optional.of(paid), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(session.packageCode()).isEqualTo(CatalogPackages.ULTIMATE_PACKAGE);
    }

    @Test
    void decide_whenUsablePaidAndActiveLog_thenAllowPaid() {
        TrialLog log = TrialLog.builder()
                .packageCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .status(TrialLogStatus.ACTIVE)
                .endsAt(now.plusDays(10))
                .build();
        Purchase paid = paid(SubscriptionStatus.ACTIVE);
        AccessSession session = policy.decide(now.minusDays(2), Optional.of(log), Optional.of(paid), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(session.packageCode()).isEqualTo(CatalogPackages.ULTIMATE_PACKAGE);
    }

    @Test
    void decide_whenPaidPastDue_thenRequirePayment() {
        Purchase paid = paid(SubscriptionStatus.PAST_DUE);
        AccessSession session = policy.decide(now.minusDays(20), Optional.empty(), Optional.of(paid), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.REQUIRE_PAYMENT);
        assertThat(session.messageKey()).isEqualTo("require_payment");
    }

    @Test
    void decide_whenPaidGracePeriod_thenRequirePayment() {
        Purchase paid = paid(SubscriptionStatus.GRACE_PERIOD);
        paid.setSubscriptionGraceEndsAt(now.plusDays(2));
        AccessSession session = policy.decide(now.minusDays(3), Optional.empty(), Optional.of(paid), now);
        assertThat(session.decision()).isEqualTo(AccessDecision.REQUIRE_PAYMENT);
        assertThat(session.debtDueAt()).isEqualTo(now.plusDays(2));
    }

    private Purchase paid(SubscriptionStatus subscriptionStatus) {
        return Purchase.builder()
                .id(10L)
                .packageCode(CatalogPackages.ULTIMATE_PACKAGE)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .subscriptionStatus(subscriptionStatus)
                .startsAt(now.minusDays(5))
                .expiresAt(now.plusDays(25))
                .build();
    }
}
