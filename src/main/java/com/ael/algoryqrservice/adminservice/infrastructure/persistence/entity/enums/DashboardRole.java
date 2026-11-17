package com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums;

/** What an admin panel account may change; every role can read everything except admin-only screens. */
public enum DashboardRole {
    /** Super admin: everything, including admin accounts, the audit log and system screens. */
    ADMIN,
    /** Payments, billing, collections, coupons, catalog and member packages. */
    FINANCE,
    /** Members, feedback, support tickets, collection notes and site content. */
    SUPPORT,
    /** Read-only. */
    VIEWER;

    public String authority() {
        return "ROLE_DASHBOARD_" + name();
    }
}
