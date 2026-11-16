package com.ael.algoryqrservice.shared.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Cluster-wide lock for scheduled jobs, kept in the {@code scheduler_lock} table. Every instance that
 * points at the same database (a second server, or a developer machine on the stage database) runs
 * the same {@code @Scheduled} methods; without this a subscription could be charged twice.
 *
 * <p>A lock is held until the job finishes or {@code atMost} passes, whichever comes first, so a
 * crashed instance never blocks a job for longer than {@code atMost}.
 */
@Component
@Slf4j
public class SchedulerLock {

    private static final String OWNER = ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTransaction;
    private final TransactionTemplate jobTransaction;

    public SchedulerLock(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.jobTransaction = new TransactionTemplate(transactionManager);
    }

    /** Like {@link #runExclusively} with the whole task in one transaction. */
    public boolean runExclusivelyInTransaction(String name, Duration atMost, Runnable task) {
        return runExclusively(name, atMost, () -> jobTransaction.executeWithoutResult(status -> task.run()));
    }

    /** Runs {@code task} only if no other instance holds {@code name}; returns whether it ran. */
    public boolean runExclusively(String name, Duration atMost, Runnable task) {
        if (!acquire(name, atMost)) {
            log.debug("Scheduled job skipped, lock held elsewhere. job={}", name);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            release(name);
        }
    }

    boolean acquire(String name, Duration atMost) {
        Instant now = Instant.now();
        Timestamp until = Timestamp.from(now.plus(atMost));
        Timestamp nowTs = Timestamp.from(now);
        Boolean acquired = newTransaction.execute(status -> {
            int updated = jdbc.update(
                    "UPDATE scheduler_lock SET locked_until = ?, locked_at = ?, locked_by = ? "
                            + "WHERE name = ? AND locked_until <= ?",
                    until, nowTs, OWNER, name, nowTs);
            if (updated == 1) {
                return true;
            }
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM scheduler_lock WHERE name = ?", Integer.class, name);
            if (exists == null || exists != 0) {
                return false;
            }
            try {
                insert(name, until, nowTs);
                return true;
            } catch (DataIntegrityViolationException raced) {
                // Another instance inserted first. Postgres has aborted this transaction, so roll it
                // back instead of letting the commit fail.
                status.setRollbackOnly();
                return false;
            }
        });
        return Boolean.TRUE.equals(acquired);
    }

    private void insert(String name, Timestamp until, Timestamp now) {
        jdbc.update(
                "INSERT INTO scheduler_lock (name, locked_until, locked_at, locked_by) VALUES (?, ?, ?, ?)",
                name, until, now, OWNER);
    }

    void release(String name) {
        try {
            newTransaction.executeWithoutResult(status -> jdbc.update(
                    "UPDATE scheduler_lock SET locked_until = ? WHERE name = ? AND locked_by = ?",
                    Timestamp.from(Instant.now()), name, OWNER));
        } catch (RuntimeException exception) {
            // The lock still expires at locked_until; a failed release only delays the next run.
            log.warn("Scheduler lock release failed. job={}", name, exception);
        }
    }
}
