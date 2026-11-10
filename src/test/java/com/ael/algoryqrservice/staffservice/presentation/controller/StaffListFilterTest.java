package com.ael.algoryqrservice.staffservice.presentation.controller;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.enums.StaffRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffListFilterTest {

    @Test
    void readsSeveralRolesAndKeepsTheOldSingleParameter() {
        assertThat(StaffListFilter.parseRoles("waiter, KITCHEN", null))
                .containsExactlyInAnyOrder(StaffRole.WAITER, StaffRole.KITCHEN);
        assertThat(StaffListFilter.parseRoles(null, StaffRole.COURIER)).containsExactly(StaffRole.COURIER);
        assertThat(StaffListFilter.parseRoles("", null)).isEmpty();
    }

    @Test
    void readsBranchIdsAndTheNoBranchMarker() {
        StaffListFilter.Branches branches = StaffListFilter.parseBranches("5, 24,none,5", null);
        assertThat(branches.ids()).containsExactly(5L, 24L);
        assertThat(branches.includeNoBranch()).isTrue();

        StaffListFilter.Branches legacy = StaffListFilter.parseBranches(null, 12L);
        assertThat(legacy.ids()).containsExactly(12L);
        assertThat(legacy.includeNoBranch()).isFalse();
    }

    @Test
    void rejectsUnknownValues() {
        assertThatThrownBy(() -> StaffListFilter.parseRoles("BOSS", null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> StaffListFilter.parseBranches("abc", null)).isInstanceOf(BadRequestException.class);
    }
}
