package com.ael.algoryqrservice.trial.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TrialPolicyTest {

    @Test
    void canStart_onlyNeverStarted() {
        assertThat(TrialPolicy.canStart(TrialSnapshot.neverStarted())).isTrue();
        assertThat(TrialPolicy.canStart(TrialSnapshot.active(1L, LocalDateTime.now()))).isFalse();
        assertThat(TrialPolicy.canStart(TrialSnapshot.expired(1L, LocalDateTime.now()))).isFalse();
        assertThat(TrialPolicy.canStart(TrialSnapshot.blocked(1L, LocalDateTime.now(), true))).isFalse();
    }

    @Test
    void canExtend_whenNotBlocked() {
        assertThat(TrialPolicy.canExtend(TrialSnapshot.neverStarted())).isTrue();
        assertThat(TrialPolicy.canExtend(TrialSnapshot.active(1L, LocalDateTime.now()))).isTrue();
        assertThat(TrialPolicy.canExtend(TrialSnapshot.expired(1L, LocalDateTime.now()))).isTrue();
        assertThat(TrialPolicy.canExtend(TrialSnapshot.blocked(null, null, false))).isFalse();
    }

    @Test
    void canEnd_onlyActive() {
        assertThat(TrialPolicy.canEnd(TrialSnapshot.active(1L, LocalDateTime.now()))).isTrue();
        assertThat(TrialPolicy.canEnd(TrialSnapshot.neverStarted())).isFalse();
        assertThat(TrialPolicy.canEnd(TrialSnapshot.expired(1L, LocalDateTime.now()))).isFalse();
        assertThat(TrialPolicy.canEnd(TrialSnapshot.blocked(1L, LocalDateTime.now(), true))).isFalse();
    }
}
