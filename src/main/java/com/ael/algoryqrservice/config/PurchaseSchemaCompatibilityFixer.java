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
public class PurchaseSchemaCompatibilityFixer implements ApplicationRunner {

    private static final String[] PURCHASE_NULLABLE_COLUMNS = {
            "expires_at",
            "starts_at",
            "customer_id"
    };

    private static final String[] PURCHASE_LOG_NULLABLE_COLUMNS = {
            "customer_id",
            "purchase_id",
            "user_id"
    };

    private static final String[] USER_ENTITLEMENT_NULLABLE_COLUMNS = {
            "customer_id"
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (String column : PURCHASE_NULLABLE_COLUMNS) {
            dropNotNullIfPresent("tbl_purchase", column);
        }
        for (String column : PURCHASE_LOG_NULLABLE_COLUMNS) {
            dropNotNullIfPresent("tbl_purchase_log", column);
        }
        for (String column : USER_ENTITLEMENT_NULLABLE_COLUMNS) {
            dropNotNullIfPresent("tbl_user_entitlement", column);
        }
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
