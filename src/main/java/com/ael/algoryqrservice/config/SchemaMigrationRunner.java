package com.ael.algoryqrservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class SchemaMigrationRunner implements ApplicationRunner {

    private static final String MIGRATION_TABLE = "tbl_schema_migration";
    private static final String MIGRATION_LOCATION = "classpath:schema/*.sql";

    private final DataSource dataSource;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        ensureMigrationTable();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(MIGRATION_LOCATION);
        Arrays.stream(resources)
                .sorted(Comparator.comparing(Resource::getFilename))
                .forEach(this::applyIfPending);
    }

    private void ensureMigrationTable() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS tbl_schema_migration (
                    id VARCHAR(128) PRIMARY KEY,
                    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """
        );
    }

    private void applyIfPending(Resource resource) {
        String migrationId = resource.getFilename();
        if (migrationId == null || migrationId.isBlank()) {
            return;
        }
        Boolean applied = jdbcTemplate.query(
                "SELECT EXISTS (SELECT 1 FROM " + MIGRATION_TABLE + " WHERE id = ?)",
                rs -> rs.next() ? rs.getBoolean(1) : false,
                migrationId
        );
        if (Boolean.TRUE.equals(applied)) {
            return;
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(resource);
        populator.setContinueOnError(false);
        populator.execute(dataSource);
        jdbcTemplate.update("INSERT INTO " + MIGRATION_TABLE + " (id) VALUES (?)", migrationId);
        log.info("Applied schema migration {}", migrationId);
    }
}
