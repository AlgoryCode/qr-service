package com.ael.algoryqrservice.fulfillmentservice.application.snapshot;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps {@code merchant_entitlements} — what each merchant may use right now — in step with the
 * grants. Writers only mark a merchant dirty; the rebuild runs once per merchant just before the
 * transaction commits, after Hibernate has flushed, so it sees exactly what is being committed.
 * Rows carry the earliest expiry of their grants: a read past it rebuilds first, so an add-on that
 * lapses between events never keeps granting.
 */
@Slf4j
@Service
public class EntitlementSnapshotService {

    /** One feature as the merchant may use it now. {@code limit} is meaningless when unlimited. */
    public record Effective(String feature, long limit, boolean unlimited, int grants, Instant validUntil) {
    }

    public record Snapshot(long version, Map<String, Effective> features) {

        public Optional<Effective> feature(String key) {
            return Optional.ofNullable(features.get(key));
        }
    }

    private static final String DIRTY_KEY = EntitlementSnapshotService.class.getName() + ".dirty";

    @PersistenceContext
    private EntityManager entityManager;

    /** Merchants whose snapshot is kept in memory before the cache starts over. */
    private static final int CACHE_LIMIT = 20_000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTransaction;
    private final Map<Long, Snapshot> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private List<EntitlementChangeListener> listeners = List.of();

    public EntitlementSnapshotService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Rebuild this merchant's snapshot when the current transaction commits (or now, outside one). */
    public void markDirty(Long merchantId) {
        if (merchantId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            rebuild(merchantId);
            notifyChanged(merchantId);
            return;
        }
        dirtySet().add(merchantId);
    }

    /** Same, for writes that only know the purchase (add-on refunds and extensions). */
    public void markDirtyByPurchase(Long purchaseId) {
        if (purchaseId == null) {
            return;
        }
        jdbc.queryForList("select distinct merchant_id from entitlement_grants where purchase_id = ?", Long.class, purchaseId)
                .forEach(this::markDirty);
    }

    @SuppressWarnings("unchecked")
    private Set<Long> dirtySet() {
        Set<Long> dirty = (Set<Long>) TransactionSynchronizationManager.getResource(DIRTY_KEY);
        if (dirty != null) {
            return dirty;
        }
        Set<Long> created = new LinkedHashSet<>();
        TransactionSynchronizationManager.bindResource(DIRTY_KEY, created);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                if (readOnly) {
                    return;
                }
                entityManager.flush();
                created.forEach(EntitlementSnapshotService.this::rebuild);
            }

            @Override
            public void afterCommit() {
                created.forEach(EntitlementSnapshotService.this::notifyChanged);
            }

            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(DIRTY_KEY);
            }
        });
        return created;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setListeners(List<EntitlementChangeListener> listeners) {
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
    }

    private void notifyChanged(Long merchantId) {
        for (EntitlementChangeListener listener : listeners) {
            try {
                listener.entitlementsChanged(merchantId);
            } catch (RuntimeException exception) {
                log.warn("entitlement_change_listener_failed merchantId={} listener={}: {}",
                        merchantId, listener.getClass().getSimpleName(), exception.getMessage());
            }
        }
    }

    /** Recomputes the merchant's effective features and the subscription mirror, and bumps the version. */
    public void rebuild(Long merchantId) {
        jdbc.update("delete from merchant_entitlements where merchant_id = ?", merchantId);
        jdbc.update("""
                insert into merchant_entitlements (merchant_id, feature_key, limit_value, unlimited, grants, valid_until, updated_at)
                select e.merchant_id, e.feature_code,
                       sum(case when e.unlimited then 0 else e.quantity end),
                       bool_or(e.unlimited), count(*), min(e.expires_at), ?
                from entitlement_grants e
                join merchant_subscriptions ap on ap.merchant_id = e.merchant_id
                where e.merchant_id = ?
                  and e.status = 'ACTIVE'
                  and (e.expires_at is null or e.expires_at > ?)
                  and ap.status in ('ACTIVE', 'GRACE_PERIOD')
                group by e.merchant_id, e.feature_code
                """, Timestamp.from(Instant.now()), merchantId, Timestamp.from(Instant.now()));
        jdbc.update("""
                update merchant_subscriptions
                set entitlement_version = entitlement_version + 1,
                    plan_version_id = coalesce((select p.plan_version_id from purchases p where p.id = merchant_subscriptions.purchase_id), plan_version_id),
                    billing_status = (select p.subscription_status from purchases p where p.id = merchant_subscriptions.purchase_id),
                    cancel_at_period_end = coalesce((select p.cancel_at_period_end from purchases p where p.id = merchant_subscriptions.purchase_id), false),
                    provider_subscription_id = (select p.subscription_id from purchases p where p.id = merchant_subscriptions.purchase_id)
                where merchant_id = ?
                """, merchantId);
        log.debug("entitlement_snapshot_rebuilt merchantId={}", merchantId);
    }

    /**
     * The merchant's effective features. Rebuilt first when any of them has passed its expiry.
     * Served from memory while the stored version is unchanged: every change to the grants bumps the
     * version, so one primary-key read is enough to know the cached copy is still right on any instance.
     */
    public Snapshot read(Long merchantId) {
        long version = version(merchantId);
        Instant now = Instant.now();
        Snapshot cached = cache.get(merchantId);
        if (cached != null && cached.version() == version && !expired(cached.features().values(), now)) {
            return cached;
        }
        List<Effective> rows = load(merchantId);
        if (expired(rows, now)) {
            newTransaction.executeWithoutResult(status -> rebuild(merchantId));
            rows = load(merchantId);
            version = version(merchantId);
        }
        Snapshot snapshot = new Snapshot(version,
                rows.stream().collect(Collectors.toMap(Effective::feature, Function.identity(), (a, b) -> a)));
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(merchantId, snapshot);
        return snapshot;
    }

    private static boolean expired(java.util.Collection<Effective> rows, Instant now) {
        return rows.stream().anyMatch(row -> row.validUntil() != null && !row.validUntil().isAfter(now));
    }

    /** Version only, for cheap "has anything changed" checks by clients. */
    public long version(Long merchantId) {
        List<Long> version = jdbc.queryForList(
                "select entitlement_version from merchant_subscriptions where merchant_id = ?", Long.class, merchantId);
        return version.isEmpty() || version.get(0) == null ? 0 : version.get(0);
    }

    private List<Effective> load(Long merchantId) {
        return jdbc.query("""
                select feature_key, limit_value, unlimited, grants, valid_until
                from merchant_entitlements where merchant_id = ?
                """,
                (rs, row) -> {
                    Timestamp until = rs.getTimestamp("valid_until");
                    return new Effective(rs.getString("feature_key"), rs.getLong("limit_value"),
                            rs.getBoolean("unlimited"), rs.getInt("grants"), until == null ? null : until.toInstant());
                },
                merchantId);
    }
}
