package com.ael.algoryqrservice.trial.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TrialSnapshotFactoryTest {

    @Test
    void from_whenBlocked_thenBlockedRegardlessOfTrial() {
        TrialSnapshot snapshot = TrialSnapshotFactory.from(9L, LocalDateTime.now(), true, true, true);
        assertThat(snapshot.lifecycle()).isEqualTo(TrialLifecycle.BLOCKED_BY_PAID);
        assertThat(snapshot.consumed()).isTrue();
    }

    @Test
    void from_whenNoTrial_thenNeverStarted() {
        TrialSnapshot snapshot = TrialSnapshotFactory.from(null, null, false, false, false);
        assertThat(snapshot.lifecycle()).isEqualTo(TrialLifecycle.NEVER_STARTED);
        assertThat(snapshot.consumed()).isFalse();
    }

    @Test
    void from_whenUsableTrial_thenActive() {
        LocalDateTime expires = LocalDateTime.now().plusDays(3);
        TrialSnapshot snapshot = TrialSnapshotFactory.from(4L, expires, true, true, false);
        assertThat(snapshot.lifecycle()).isEqualTo(TrialLifecycle.ACTIVE);
        assertThat(snapshot.expiresAt()).isEqualTo(expires);
    }

    @Test
    void from_whenUnusableTrial_thenExpired() {
        LocalDateTime expires = LocalDateTime.now().minusDays(1);
        TrialSnapshot snapshot = TrialSnapshotFactory.from(4L, expires, false, true, false);
        assertThat(snapshot.lifecycle()).isEqualTo(TrialLifecycle.EXPIRED);
        assertThat(snapshot.consumed()).isTrue();
    }
}
