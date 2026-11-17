package com.ael.algoryqrservice.adminservice.security;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPermissionsTest {

    private static final String P = AdminPermissions.ADMIN_PREFIX;

    @Test
    void superAdminCanDoEverything() {
        assertThat(AdminPermissions.allowed(DashboardRole.ADMIN, "DELETE", P + "/dashboard-users/3")).isTrue();
        assertThat(AdminPermissions.allowed(DashboardRole.ADMIN, "POST", P + "/anything/new")).isTrue();
    }

    @Test
    void adminOnlyAreasAreHiddenFromOtherRolesEvenForReads() {
        for (DashboardRole role : new DashboardRole[]{DashboardRole.FINANCE, DashboardRole.SUPPORT, DashboardRole.VIEWER}) {
            assertThat(AdminPermissions.allowed(role, "GET", P + "/audit-log")).isFalse();
            assertThat(AdminPermissions.allowed(role, "GET", P + "/dashboard-users")).isFalse();
        }
    }

    @Test
    void viewerReadsButNeverWrites() {
        assertThat(AdminPermissions.allowed(DashboardRole.VIEWER, "GET", P + "/payments")).isTrue();
        assertThat(AdminPermissions.allowed(DashboardRole.VIEWER, "POST", P + "/coupons")).isFalse();
        assertThat(AdminPermissions.allowed(DashboardRole.VIEWER, "PATCH", P + "/users/5")).isFalse();
    }

    @Test
    void financeWritesMoneyAndPackageGrantsButNotSupportAreas() {
        assertThat(AdminPermissions.allowed(DashboardRole.FINANCE, "POST", P + "/coupons")).isTrue();
        assertThat(AdminPermissions.allowed(DashboardRole.FINANCE, "PUT", P + "/users/9/package")).isTrue();
        assertThat(AdminPermissions.allowed(DashboardRole.FINANCE, "PATCH", P + "/users/9")).isFalse();
        assertThat(AdminPermissions.allowed(DashboardRole.FINANCE, "POST", P + "/announcements")).isFalse();
    }

    @Test
    void supportWritesMembersAndCollectionsButNotPrices() {
        assertThat(AdminPermissions.allowed(DashboardRole.SUPPORT, "PATCH", P + "/users/9")).isTrue();
        assertThat(AdminPermissions.allowed(DashboardRole.SUPPORT, "POST", P + "/billing/collections/4/events")).isTrue();
        assertThat(AdminPermissions.allowed(DashboardRole.SUPPORT, "PUT", P + "/users/9/package")).isFalse();
        assertThat(AdminPermissions.allowed(DashboardRole.SUPPORT, "POST", P + "/products")).isFalse();
    }

    @Test
    void unknownWritesAreSuperAdminOnly() {
        assertThat(AdminPermissions.allowed(DashboardRole.FINANCE, "POST", P + "/mystery")).isFalse();
        assertThat(AdminPermissions.allowed(DashboardRole.FINANCE, "GET", P + "/mystery")).isTrue();
    }
}
