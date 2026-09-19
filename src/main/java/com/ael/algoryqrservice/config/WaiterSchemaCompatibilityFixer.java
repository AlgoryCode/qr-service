package com.ael.algoryqrservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Flyway is disabled; add waiter/kitchen columns before Hibernate validate.
 */
@Component
@Slf4j
public class WaiterSchemaCompatibilityFixer implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && "dataSource".equals(beanName)) {
            ensureSchema(dataSource);
        }
        return bean;
    }

    private void ensureSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            dropNotNullIfPresent(statement, "tbl_menu_waiter", "menu_id");
            statement.execute(
                    "ALTER TABLE tbl_menu_waiter ADD COLUMN IF NOT EXISTS staff_role VARCHAR(16) NOT NULL DEFAULT 'WAITER'"
            );
            statement.execute(
                    "ALTER TABLE tbl_branch ADD COLUMN IF NOT EXISTS kitchen_enabled BOOLEAN NOT NULL DEFAULT FALSE"
            );
            statement.execute("ALTER TABLE tbl_menu_order ADD COLUMN IF NOT EXISTS kitchen_note TEXT");
            statement.execute("ALTER TABLE tbl_menu_order DROP CONSTRAINT IF EXISTS tbl_menu_order_status_check");
            statement.execute("""
                    ALTER TABLE tbl_menu_order
                        ADD CONSTRAINT tbl_menu_order_status_check
                        CHECK (status::text = ANY (ARRAY[
                            'DRAFT','SUBMITTED','CONFIRMED','PREPARING','READY','SERVED','REJECTED','CANCELLED'
                        ]::text[]))
                    """);
            log.info("Ensured waiter/kitchen schema columns");
        } catch (Exception e) {
            throw new IllegalStateException("Waiter schema could not be initialized", e);
        }
    }

    private void dropNotNullIfPresent(Statement statement, String tableName, String columnName) throws Exception {
        boolean notNull;
        try (ResultSet rs = statement.executeQuery("""
                SELECT is_nullable = 'NO'
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = '%s'
                  AND column_name = '%s'
                """.formatted(tableName, columnName))) {
            notNull = rs.next() && rs.getBoolean(1);
        }
        if (!notNull) {
            return;
        }
        statement.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " DROP NOT NULL");
        log.info("Dropped NOT NULL on {}.{}", tableName, columnName);
    }
}
