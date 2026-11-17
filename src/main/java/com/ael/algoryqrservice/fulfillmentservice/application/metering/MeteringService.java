package com.ael.algoryqrservice.fulfillmentservice.application.metering;

import com.ael.algoryqrservice.fulfillmentservice.application.snapshot.EntitlementSnapshotService;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

/**
 * One meter for every per-period allowance (free smart reports, free assistant questions, AI menu
 * imports). Usage lives in {@code usage_counters} per merchant, feature and period; each spend is an
 * {@code usage_events} row keyed by the caller's idempotency key, so a retried request is charged
 * once and a failed job can be given back. The allowance is what the merchant's grants say, else the
 * feature's {@code free_quota}; owning the parent feature makes it unlimited where the feature says so.
 *
 * <p>Spending joins the caller's transaction, so a rolled-back request gives its use back too.
 */
@Service
public class MeteringService {

    /** Usage of one metered feature in the current period; {@code limit} is null when unlimited. */
    public record Usage(String feature, String periodKey, long used, Long limit, Instant resetsAt) {
        public boolean unlimited() {
            return limit == null;
        }

        public Long remaining() {
            return limit == null ? null : Math.max(limit - used, 0);
        }
    }

    /** Result of a spend: {@code replayed} when the idempotency key was already charged. */
    public record Charge(boolean allowed, boolean replayed, Usage usage) {
    }

    record Definition(String key, String resetPeriod, String parentKey, Integer freeQuota, boolean unlimitedWithParent) {
    }

    record Period(String key, Instant resetsAt) {
    }

    private record Spend(long id, long merchantId, String feature, String periodKey, int quantity) {
    }

    private final JdbcTemplate jdbc;
    private final EntitlementSnapshotService snapshots;
    private final TransactionTemplate newTransaction;

    public MeteringService(JdbcTemplate jdbc, EntitlementSnapshotService snapshots,
                           PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.snapshots = snapshots;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Usage usage(Long merchantId, String featureKey) {
        Definition definition = definition(featureKey);
        Period period = period(definition.resetPeriod());
        return new Usage(featureKey, period.key(), used(merchantId, featureKey, period.key()),
                limit(merchantId, definition), period.resetsAt());
    }

    /**
     * Spends {@code quantity} uses if the period's allowance has room. Never throws for an exhausted
     * allowance: the caller decides what a refusal means (fall back to credits, answer 403, ...).
     */
    public Charge consume(Long merchantId, String featureKey, int quantity, String idempotencyKey, String reference) {
        if (merchantId == null || quantity <= 0 || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("merchantId, a positive quantity and an idempotency key are required");
        }
        Definition definition = definition(featureKey);
        if (findSpend(idempotencyKey, false).isPresent()) {
            return new Charge(true, true, usage(merchantId, featureKey));
        }
        Period period = period(definition.resetPeriod());
        Long limit = limit(merchantId, definition);
        ensureCounter(merchantId, featureKey, period.key());
        Timestamp now = Timestamp.from(Instant.now());
        int updated = limit == null
                ? jdbc.update("update usage_counters set used = used + ?, updated_at = ? "
                        + "where merchant_id = ? and feature_key = ? and period_key = ?",
                quantity, now, merchantId, featureKey, period.key())
                : jdbc.update("update usage_counters set used = used + ?, updated_at = ? "
                        + "where merchant_id = ? and feature_key = ? and period_key = ? and used + ? <= ?",
                quantity, now, merchantId, featureKey, period.key(), quantity, limit);
        if (updated == 0) {
            return new Charge(false, false,
                    new Usage(featureKey, period.key(), used(merchantId, featureKey, period.key()), limit, period.resetsAt()));
        }
        jdbc.update("insert into usage_events (merchant_id, feature_key, period_key, quantity, idempotency_key, reference, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                merchantId, featureKey, period.key(), quantity, idempotencyKey, reference, now);
        return new Charge(true, false,
                new Usage(featureKey, period.key(), used(merchantId, featureKey, period.key()), limit, period.resetsAt()));
    }

    /** Gives a spend back (a failed job, an unanswered question). False when unknown or already given back. */
    public boolean release(String idempotencyKey) {
        return release(null, idempotencyKey);
    }

    /** As {@link #release(String)}, only for a spend of {@code merchantId}. */
    public boolean release(Long merchantId, String idempotencyKey) {
        Optional<Spend> spend = findSpend(idempotencyKey, true)
                .filter(found -> merchantId == null || found.merchantId() == merchantId);
        if (spend.isEmpty()) {
            return false;
        }
        Spend found = spend.get();
        Timestamp now = Timestamp.from(Instant.now());
        if (jdbc.update("update usage_events set released_at = ? where id = ? and released_at is null", now, found.id()) == 0) {
            return false;
        }
        jdbc.update("update usage_counters set used = case when used > ? then used - ? else 0 end, updated_at = ? "
                        + "where merchant_id = ? and feature_key = ? and period_key = ?",
                found.quantity(), found.quantity(), now, found.merchantId(), found.feature(), found.periodKey());
        return true;
    }

    Definition definition(String featureKey) {
        List<Definition> rows = jdbc.query(
                "select feature_key, kind, reset_period, parent_key, free_quota, unlimited_with_parent from features where feature_key = ?",
                (rs, i) -> {
                    if (!"METERED".equals(rs.getString("kind"))) {
                        throw new IllegalArgumentException("Not a metered feature: " + featureKey);
                    }
                    int free = rs.getInt("free_quota");
                    return new Definition(rs.getString("feature_key"), rs.getString("reset_period"), rs.getString("parent_key"),
                            rs.wasNull() ? null : free, rs.getBoolean("unlimited_with_parent"));
                },
                featureKey);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Unknown feature: " + featureKey);
        }
        return rows.get(0);
    }

    /** What the merchant may use this period; null when unlimited. */
    Long limit(Long merchantId, Definition definition) {
        EntitlementSnapshotService.Snapshot snapshot = snapshots.read(merchantId);
        Optional<EntitlementSnapshotService.Effective> granted = snapshot.feature(definition.key());
        if (granted.isPresent()) {
            return granted.get().unlimited() ? null : granted.get().limit();
        }
        if (definition.unlimitedWithParent() && definition.parentKey() != null
                && snapshot.feature(definition.parentKey()).isPresent()) {
            return null;
        }
        return definition.freeQuota() == null ? 0L : definition.freeQuota().longValue();
    }

    static Period period(String resetPeriod) {
        LocalDate today = LocalDate.now(AppTime.clock());
        return switch (resetPeriod == null ? "NONE" : resetPeriod) {
            case "DAY" -> new Period(today.toString(), startOf(today.plusDays(1)));
            case "WEEK" -> new Period(
                    "%d-W%02d".formatted(today.get(IsoFields.WEEK_BASED_YEAR), today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)),
                    startOf(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))));
            case "MONTH" -> new Period("%d-%02d".formatted(today.getYear(), today.getMonthValue()),
                    startOf(today.with(TemporalAdjusters.firstDayOfNextMonth())));
            default -> new Period("ALL", null);
        };
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(AppTime.ZONE).toInstant();
    }

    private long used(Long merchantId, String featureKey, String periodKey) {
        List<Long> used = jdbc.queryForList(
                "select used from usage_counters where merchant_id = ? and feature_key = ? and period_key = ?",
                Long.class, merchantId, featureKey, periodKey);
        return used.isEmpty() || used.get(0) == null ? 0 : used.get(0);
    }

    /**
     * Creates the period's counter row in its own transaction, so a concurrent first spend that
     * loses the insert race cannot abort the caller's transaction.
     */
    private void ensureCounter(Long merchantId, String featureKey, String periodKey) {
        Integer existing = jdbc.queryForObject(
                "select count(*) from usage_counters where merchant_id = ? and feature_key = ? and period_key = ?",
                Integer.class, merchantId, featureKey, periodKey);
        if (existing != null && existing > 0) {
            return;
        }
        try {
            newTransaction.executeWithoutResult(status -> jdbc.update(
                    "insert into usage_counters (merchant_id, feature_key, period_key, used, updated_at) values (?, ?, ?, 0, ?)",
                    merchantId, featureKey, periodKey, Timestamp.from(Instant.now())));
        } catch (DataIntegrityViolationException alreadyCreated) {
            // Another request created it first.
        }
    }

    private Optional<Spend> findSpend(String idempotencyKey, boolean openOnly) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("select id, merchant_id, feature_key, period_key, quantity from usage_events where idempotency_key = ?"
                                + (openOnly ? " and released_at is null" : ""),
                        (rs, i) -> new Spend(rs.getLong("id"), rs.getLong("merchant_id"), rs.getString("feature_key"),
                                rs.getString("period_key"), rs.getInt("quantity")),
                        idempotencyKey)
                .stream().findFirst();
    }
}
