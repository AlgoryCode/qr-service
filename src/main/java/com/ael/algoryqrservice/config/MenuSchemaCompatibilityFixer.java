package com.ael.algoryqrservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MenuSchemaCompatibilityFixer implements ApplicationRunner {

    private static final String[] LEGACY_MENU_COLUMNS = {
            "public_slug",
            "url_mode"
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (String column : LEGACY_MENU_COLUMNS) {
            dropColumnIfPresent("tbl_menu", column);
        }
    }

    private void dropColumnIfPresent(String tableName, String columnName) {
        Boolean exists = jdbcTemplate.query(
                """
                SELECT COUNT(*) > 0
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """,
                rs -> rs.next() ? rs.getBoolean(1) : false,
                tableName,
                columnName
        );
        if (!Boolean.TRUE.equals(exists)) {
            return;
        }
        jdbcTemplate.execute(
                "ALTER TABLE " + tableName + " DROP COLUMN IF EXISTS " + columnName
        );
        log.info("Dropped legacy column {}.{}", tableName, columnName);
    }
}
