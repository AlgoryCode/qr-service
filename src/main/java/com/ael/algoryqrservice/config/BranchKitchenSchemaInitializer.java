package com.ael.algoryqrservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Slf4j
public class BranchKitchenSchemaInitializer implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && "dataSource".equals(beanName)) {
            ensureSchema(dataSource);
        }
        return bean;
    }

    private void ensureSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE tbl_branch ADD COLUMN IF NOT EXISTS kitchen_enabled BOOLEAN NOT NULL DEFAULT FALSE"
            );
            try {
                statement.execute("""
                        UPDATE tbl_branch b
                        SET kitchen_enabled = TRUE
                        WHERE EXISTS (
                            SELECT 1
                            FROM tbl_merchant_staff w
                            WHERE w.branch_id = b.id
                              AND w.staff_role = 'KITCHEN'
                        )
                        """);
            } catch (Exception backfillError) {
                log.warn("Could not backfill kitchen_enabled from existing kitchen staff", backfillError);
            }
            log.info("Ensured branch kitchen_enabled schema");
        } catch (Exception e) {
            throw new IllegalStateException("Branch kitchen schema could not be initialized", e);
        }
    }
}
