package com.ael.algoryqrservice.fulfillmentservice.application.snapshot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntitlementSnapshotCacheTest {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;
    private EntitlementSnapshotService snapshots;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName("snap-" + UUID.randomUUID()).build();
        jdbc = new JdbcTemplate(db);
        jdbc.execute("create table merchant_subscriptions (merchant_id bigint primary key, entitlement_version bigint not null)");
        jdbc.execute("""
                create table merchant_entitlements (merchant_id bigint, feature_key varchar(64), limit_value bigint,
                  unlimited boolean, grants int, valid_until timestamp, updated_at timestamp)""");
        jdbc.update("insert into merchant_subscriptions values (1, 3)");
        jdbc.update("insert into merchant_entitlements values (1, 'QR_BRANCH', 2, false, 1, null, current_timestamp)");
        snapshots = new EntitlementSnapshotService(jdbc, new DataSourceTransactionManager(db));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void sameVersionIsServedFromMemory() {
        assertThat(snapshots.read(1L).feature("QR_BRANCH").orElseThrow().limit()).isEqualTo(2);

        // Changed behind the service's back without a version bump: the cached copy is still served.
        jdbc.update("update merchant_entitlements set limit_value = 9");
        assertThat(snapshots.read(1L).feature("QR_BRANCH").orElseThrow().limit()).isEqualTo(2);
    }

    @Test
    void newVersionIsReadAgain() {
        snapshots.read(1L);
        jdbc.update("update merchant_entitlements set limit_value = 5");
        jdbc.update("update merchant_subscriptions set entitlement_version = 4");

        EntitlementSnapshotService.Snapshot snapshot = snapshots.read(1L);
        assertThat(snapshot.version()).isEqualTo(4);
        assertThat(snapshot.feature("QR_BRANCH").orElseThrow().limit()).isEqualTo(5);
    }
}
