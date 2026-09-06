package com.ael.algoryqrservice.trial.domain;

import java.time.LocalDateTime;

public record TrialSnapshot(
        TrialLifecycle lifecycle,
        Long purchaseId,
        LocalDateTime expiresAt,
        boolean consumed
) {
    public static TrialSnapshot neverStarted() {
        return new TrialSnapshot(TrialLifecycle.NEVER_STARTED, null, null, false);
    }

    public static TrialSnapshot active(Long purchaseId, LocalDateTime expiresAt) {
        return new TrialSnapshot(TrialLifecycle.ACTIVE, purchaseId, expiresAt, true);
    }

    public static TrialSnapshot expired(Long purchaseId, LocalDateTime expiresAt) {
        return new TrialSnapshot(TrialLifecycle.EXPIRED, purchaseId, expiresAt, true);
    }

    public static TrialSnapshot blocked(Long purchaseId, LocalDateTime expiresAt, boolean consumed) {
        return new TrialSnapshot(TrialLifecycle.BLOCKED_BY_PAID, purchaseId, expiresAt, consumed);
    }
}
