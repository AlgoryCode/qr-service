package com.ael.algoryqrservice.shared.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulerLockTest {

    private JdbcTemplate jdbc;
    private SchedulerLock lock;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:lock-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE scheduler_lock (name VARCHAR(64) PRIMARY KEY, locked_until TIMESTAMP NOT NULL, "
                + "locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL)");
        lock = new SchedulerLock(jdbc, new DataSourceTransactionManager(dataSource));
    }

    @Test
    void runsTheTaskAndReleasesTheLock() {
        AtomicInteger runs = new AtomicInteger();

        assertThat(lock.runExclusively("job", Duration.ofMinutes(5), runs::incrementAndGet)).isTrue();
        assertThat(lock.runExclusively("job", Duration.ofMinutes(5), runs::incrementAndGet)).isTrue();

        assertThat(runs).hasValue(2);
    }

    @Test
    void skipsWhileAnotherRunHoldsTheLock() {
        AtomicBoolean innerRan = new AtomicBoolean();

        lock.runExclusively("job", Duration.ofMinutes(5), () ->
                assertThat(lock.runExclusively("job", Duration.ofMinutes(5), () -> innerRan.set(true))).isFalse());

        assertThat(innerRan).isFalse();
    }

    @Test
    void differentJobsDoNotBlockEachOther() {
        AtomicBoolean otherRan = new AtomicBoolean();

        lock.runExclusively("a", Duration.ofMinutes(5), () ->
                lock.runExclusively("b", Duration.ofMinutes(5), () -> otherRan.set(true)));

        assertThat(otherRan).isTrue();
    }

    @Test
    void takesOverALockWhoseHolderDiedPastItsDeadline() {
        jdbc.update("INSERT INTO scheduler_lock VALUES ('job', DATEADD('MINUTE', -1, CURRENT_TIMESTAMP), "
                + "CURRENT_TIMESTAMP, 'dead-node')");

        assertThat(lock.runExclusively("job", Duration.ofMinutes(5), () -> { })).isTrue();
    }

    @Test
    void releasesTheLockWhenTheTaskFails() {
        assertThatThrownBy(() -> lock.runExclusively("job", Duration.ofMinutes(5), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(lock.runExclusively("job", Duration.ofMinutes(5), () -> { })).isTrue();
    }
}
