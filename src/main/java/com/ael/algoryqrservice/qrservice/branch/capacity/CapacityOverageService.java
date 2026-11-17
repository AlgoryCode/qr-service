package com.ael.algoryqrservice.qrservice.branch.capacity;

import com.ael.algoryqrservice.fulfillmentservice.application.snapshot.EntitlementChangeListener;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What happens when a merchant holds more branches than the plan now allows (a downgrade, an add-on
 * that lapsed). Nothing is taken away at once: the merchant gets {@link #GRACE} to pick which
 * branches stay active. Then the extra ones become read-only, oldest kept first. Read-only branches
 * do not count against the limit, refuse edits and keep their public menus working. When the limit
 * grows again they are unlocked on their own.
 *
 * <p>A plan with no branch line at all is left alone: that is a catalog gap, not a downgrade.
 */
@Slf4j
@Service
public class CapacityOverageService implements EntitlementChangeListener {

    public static final String FEATURE = "QR_BRANCH";
    public static final Duration GRACE = Duration.ofDays(14);

    public record BranchItem(Long id, String name, boolean locked) {
    }

    /** {@code limit} is null when unlimited or not set by the plan; {@code deadline} is null when nothing is pending. */
    public record Status(boolean overLimit, Long limit, long active, long total, LocalDateTime deadline,
                         List<BranchItem> branches) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTransaction;

    public CapacityOverageService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void entitlementsChanged(Long merchantId) {
        newTransaction.executeWithoutResult(status -> check(merchantId));
    }

    /**
     * Opens a grace period when the merchant went over the limit, and unlocks branches (oldest
     * first) when the limit has room for them again.
     */
    public void check(Long merchantId) {
        Optional<Long> limit = limit(merchantId);
        if (limit.isEmpty()) {
            return;
        }
        long active = activeCount(merchantId);
        if (active > limit.get()) {
            openIfAbsent(merchantId, limit.get(), active);
            return;
        }
        long room = limit.get() - active;
        if (room > 0) {
            List<Long> unlock = jdbc.queryForList("""
                    select id from tbl_branch
                    where merchant_id = ? and is_deleted = false and capacity_locked_at is not null
                    order by id limit ?""", Long.class, merchantId, Math.min(room, Integer.MAX_VALUE));
            unlock.forEach(id -> jdbc.update("update tbl_branch set capacity_locked_at = null where id = ?", id));
            if (!unlock.isEmpty()) {
                log.info("capacity_branches_unlocked merchantId={} branches={}", merchantId, unlock);
            }
        }
        resolveOpen(merchantId, limit.get());
    }

    @Transactional(readOnly = true)
    public Status status(Long merchantId) {
        Optional<Long> limit = limit(merchantId);
        List<BranchItem> branches = jdbc.query("""
                select id, name, capacity_locked_at from tbl_branch
                where merchant_id = ? and is_deleted = false order by id""",
                (rs, i) -> new BranchItem(rs.getLong("id"), rs.getString("name"), rs.getTimestamp("capacity_locked_at") != null),
                merchantId);
        long active = branches.stream().filter(branch -> !branch.locked()).count();
        LocalDateTime deadline = jdbc.queryForList("""
                select deadline_at from capacity_overages
                where merchant_id = ? and feature_key = ? and resolved_at is null""",
                LocalDateTime.class, merchantId, FEATURE).stream().findFirst().orElse(null);
        boolean over = limit.isPresent() && active > limit.get();
        return new Status(over, limit.filter(value -> value != Long.MAX_VALUE).orElse(null), active, branches.size(),
                over ? deadline : null, branches);
    }

    /**
     * The member's pick: these branches stay active, every other one becomes read-only. Can be
     * changed later; only branches beyond the limit are ever locked.
     */
    @Transactional
    public Status keep(Long merchantId, List<Long> branchIds) {
        Set<Long> keep = new LinkedHashSet<>(branchIds == null ? List.of() : branchIds);
        List<Long> owned = jdbc.queryForList(
                "select id from tbl_branch where merchant_id = ? and is_deleted = false", Long.class, merchantId);
        if (!owned.containsAll(keep)) {
            throw new BadRequestException("BRANCH_NOT_OWNED", "Seçilen şubelerden biri bulunamadı");
        }
        Optional<Long> limit = limit(merchantId);
        if (limit.isEmpty() || owned.size() <= limit.get()) {
            jdbc.update("update tbl_branch set capacity_locked_at = null where merchant_id = ? and is_deleted = false", merchantId);
            resolveOpen(merchantId, limit.orElse(Long.MAX_VALUE));
            return status(merchantId);
        }
        if (keep.isEmpty() || keep.size() > limit.get()) {
            throw new BadRequestException("BRANCH_KEEP_COUNT",
                    "Paketiniz " + limit.get() + " şubeye izin veriyor; en az 1, en çok " + limit.get() + " şube seçin");
        }
        LocalDateTime now = AppTime.nowLocal();
        for (Long id : owned) {
            if (keep.contains(id)) {
                jdbc.update("update tbl_branch set capacity_locked_at = null where id = ?", id);
            } else {
                jdbc.update("update tbl_branch set capacity_locked_at = coalesce(capacity_locked_at, ?) where id = ?", now, id);
            }
        }
        jdbc.update("""
                update capacity_overages set resolved_at = ?, resolution = 'MEMBER_CHOICE'
                where merchant_id = ? and feature_key = ? and resolved_at is null""", now, merchantId, FEATURE);
        log.info("capacity_branches_chosen merchantId={} kept={}", merchantId, keep);
        return status(merchantId);
    }

    /**
     * Hourly: applies the oldest-first choice where the grace period ended, and catches merchants
     * who went over without an entitlement event (a branch restored, an expiry nobody read yet).
     */
    @Scheduled(fixedDelayString = "${capacity.overage.fixed-delay:3600000}", initialDelay = 120_000)
    public void sweep() {
        LocalDateTime now = AppTime.nowLocal();
        for (Long merchantId : jdbc.queryForList("""
                select merchant_id from capacity_overages
                where feature_key = ? and resolved_at is null and deadline_at <= ?""", Long.class, FEATURE, now)) {
            run(merchantId, () -> lockBeyondLimit(merchantId));
        }
        for (Long merchantId : jdbc.queryForList("""
                select distinct b.merchant_id from tbl_branch b
                where b.is_deleted = false
                  and (b.capacity_locked_at is not null
                       or exists (select 1 from merchant_entitlements me
                                  where me.merchant_id = b.merchant_id and me.feature_key = ? and me.unlimited = false
                                    and me.limit_value < (select count(*) from tbl_branch x
                                                          where x.merchant_id = b.merchant_id and x.is_deleted = false
                                                            and x.capacity_locked_at is null)))""",
                Long.class, FEATURE)) {
            run(merchantId, () -> check(merchantId));
        }
    }

    void lockBeyondLimit(Long merchantId) {
        Optional<Long> limit = limit(merchantId);
        LocalDateTime now = AppTime.nowLocal();
        if (limit.isPresent()) {
            List<Long> extra = jdbc.queryForList("""
                    select id from tbl_branch
                    where merchant_id = ? and is_deleted = false and capacity_locked_at is null
                    order by id offset ?""", Long.class, merchantId, Math.min(limit.get(), Integer.MAX_VALUE));
            extra.forEach(id -> jdbc.update("update tbl_branch set capacity_locked_at = ? where id = ?", now, id));
            if (!extra.isEmpty()) {
                log.info("capacity_branches_auto_locked merchantId={} branches={}", merchantId, extra);
            }
        }
        jdbc.update("""
                update capacity_overages set resolved_at = ?, resolution = 'AUTO'
                where merchant_id = ? and feature_key = ? and resolved_at is null""", now, merchantId, FEATURE);
    }

    /**
     * The plan's branch limit; empty when the merchant has no live plan or the plan has no branch
     * line (then nothing is locked). {@code Long.MAX_VALUE} when unlimited.
     */
    Optional<Long> limit(Long merchantId) {
        List<Long> limits = jdbc.query("""
                select case when me.unlimited then null else me.limit_value end as lim, me.unlimited
                from merchant_entitlements me
                join merchant_subscriptions ap on ap.merchant_id = me.merchant_id and ap.status in ('ACTIVE', 'GRACE_PERIOD')
                where me.merchant_id = ? and me.feature_key = ?""",
                (rs, i) -> rs.getBoolean("unlimited") ? Long.MAX_VALUE : rs.getLong("lim"),
                merchantId, FEATURE);
        return limits.stream().findFirst();
    }

    private long activeCount(Long merchantId) {
        Long count = jdbc.queryForObject("""
                select count(*) from tbl_branch
                where merchant_id = ? and is_deleted = false and capacity_locked_at is null""", Long.class, merchantId);
        return count == null ? 0 : count;
    }

    private void openIfAbsent(Long merchantId, long limit, long used) {
        Integer open = jdbc.queryForObject("""
                select count(*) from capacity_overages
                where merchant_id = ? and feature_key = ? and resolved_at is null""", Integer.class, merchantId, FEATURE);
        if (open != null && open > 0) {
            return;
        }
        LocalDateTime now = AppTime.nowLocal();
        try {
            jdbc.update("""
                    insert into capacity_overages (merchant_id, feature_key, limit_value, used_value, detected_at, deadline_at)
                    values (?, ?, ?, ?, ?, ?)""", merchantId, FEATURE, limit, used, now, now.plus(GRACE));
            log.info("capacity_overage_opened merchantId={} limit={} used={} deadline={}", merchantId, limit, used, now.plus(GRACE));
        } catch (DataIntegrityViolationException alreadyOpen) {
            // Opened by a concurrent check.
        }
    }

    private void resolveOpen(Long merchantId, long limit) {
        jdbc.update("""
                update capacity_overages
                set resolved_at = ?, resolution = case when ? > limit_value then 'LIMIT_RAISED' else 'MEMBER_CHOICE' end
                where merchant_id = ? and feature_key = ? and resolved_at is null""",
                AppTime.nowLocal(), limit, merchantId, FEATURE);
    }

    private void run(Long merchantId, Runnable work) {
        try {
            newTransaction.executeWithoutResult(status -> work.run());
        } catch (RuntimeException exception) {
            log.warn("capacity_overage_sweep_failed merchantId={}: {}", merchantId, exception.getMessage());
        }
    }
}
