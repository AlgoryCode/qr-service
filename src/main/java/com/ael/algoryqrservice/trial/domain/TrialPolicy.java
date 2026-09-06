package com.ael.algoryqrservice.trial.domain;

public final class TrialPolicy {

    private TrialPolicy() {
    }

    public static boolean canStart(TrialSnapshot snapshot) {
        return snapshot.lifecycle() == TrialLifecycle.NEVER_STARTED;
    }

    public static boolean canExtend(TrialSnapshot snapshot) {
        return switch (snapshot.lifecycle()) {
            case NEVER_STARTED, ACTIVE, EXPIRED -> true;
            case BLOCKED_BY_PAID -> false;
        };
    }

    public static boolean canEnd(TrialSnapshot snapshot) {
        return snapshot.lifecycle() == TrialLifecycle.ACTIVE;
    }
}
