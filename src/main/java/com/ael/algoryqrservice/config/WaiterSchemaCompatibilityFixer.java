package com.ael.algoryqrservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class WaiterSchemaCompatibilityFixer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        dropNotNullIfPresent("tbl_menu_waiter", "menu_id");
    }

    private void dropNotNullIfPresent(String tableName, String columnName) {
        Boolean notNull = jdbcTemplate.query(
                """
                SELECT is_nullable = 'NO'
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """,
                rs -> rs.next() ? rs.getBoolean(1) : null,
                tableName,
                columnName
        );
        if (!Boolean.TRUE.equals(notNull)) {
            return;
        }
        jdbcTemplate.execute(
                "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " DROP NOT NULL"
        );
        log.info("Dropped NOT NULL on {}.{}", tableName, columnName);
    }
}
