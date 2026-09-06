package com.ael.algoryqrservice.trial.domain;

import java.time.LocalDateTime;

public final class TrialSnapshotFactory {

    private TrialSnapshotFactory() {
    }

    public static TrialSnapshot from(
            Long trialPurchaseId,
            LocalDateTime trialExpiresAt,
            boolean trialUsable,
            boolean trialExists,
            boolean blockedByNonTrialSubscription
    ) {
        if (blockedByNonTrialSubscription) {
            return TrialSnapshot.blocked(trialPurchaseId, trialExpiresAt, trialExists);
        }
        if (!trialExists) {
            return TrialSnapshot.neverStarted();
        }
        if (trialUsable) {
            return TrialSnapshot.active(trialPurchaseId, trialExpiresAt);
        }
        return TrialSnapshot.expired(trialPurchaseId, trialExpiresAt);
    }
}
