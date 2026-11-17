package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Pins every purchase to a package version: a new purchase (or one moved to another package by a
 * plan change) takes that package's current version; an existing pin is never touched otherwise.
 * Hibernate builds this listener itself, so the JDBC access is handed over by {@link Registrar} at
 * startup. Plain SQL keeps the lookups out of Hibernate's flush.
 */
public class PurchasePlanVersionListener {

    private static volatile JdbcTemplate jdbcTemplate;

    @PrePersist
    void beforeInsert(Purchase purchase) {
        if (purchase.getPlanVersionId() == null && purchase.getPackageId() != null) {
            purchase.setPlanVersionId(currentVersion(purchase.getPackageId()));
        }
    }

    @PreUpdate
    void beforeUpdate(Purchase purchase) {
        if (purchase.getPackageId() == null) {
            return;
        }
        if (purchase.getPlanVersionId() == null
                || !Objects.equals(packageOf(purchase.getPlanVersionId()), purchase.getPackageId())) {
            purchase.setPlanVersionId(currentVersion(purchase.getPackageId()));
        }
    }

    /**
     * The merchant's subscription row mirrors the billing state of the purchase it runs on, so the
     * subscription can be read from one place. Only touches the row whose purchase this is.
     */
    @PostPersist
    @PostUpdate
    void afterSave(Purchase purchase) {
        JdbcTemplate jdbc = jdbcTemplate;
        if (jdbc == null || purchase.getId() == null) {
            return;
        }
        jdbc.update("""
                update merchant_subscriptions
                set billing_status = ?, cancel_at_period_end = ?, provider_subscription_id = ?,
                    plan_version_id = coalesce(?, plan_version_id)
                where purchase_id = ?
                """,
                purchase.getSubscriptionStatus() == null ? null : purchase.getSubscriptionStatus().name(),
                purchase.isCancelAtPeriodEnd(),
                purchase.getSubscriptionId(),
                purchase.getPlanVersionId(),
                purchase.getId());
    }

    private static Long currentVersion(Long packageId) {
        return first("select current_version_id from plans where id = ?", packageId);
    }

    private static Long packageOf(Long versionId) {
        return first("select package_id from plan_versions where id = ?", versionId);
    }

    private static Long first(String sql, Long id) {
        JdbcTemplate jdbc = jdbcTemplate;
        if (jdbc == null) {
            return null;
        }
        List<Long> ids = jdbc.queryForList(sql, Long.class, id);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Component
    static class Registrar {
        Registrar(JdbcTemplate jdbc) {
            jdbcTemplate = jdbc;
        }
    }
}
