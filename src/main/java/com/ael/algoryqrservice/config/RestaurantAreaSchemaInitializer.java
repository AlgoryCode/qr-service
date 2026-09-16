package com.ael.algoryqrservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Flyway is disabled in this service; create area tables before Hibernate validate.
 */
@Component
@Slf4j
public class RestaurantAreaSchemaInitializer implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && "dataSource".equals(beanName)) {
            ensureSchema(dataSource);
        }
        return bean;
    }

    private void ensureSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tbl_restaurant_area (
                        id BIGSERIAL PRIMARY KEY,
                        menu_id BIGINT NOT NULL,
                        name VARCHAR(120) NOT NULL,
                        sort_order INT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_restaurant_area_menu_id ON tbl_restaurant_area (menu_id)"
            );
            statement.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uk_restaurant_area_menu_name ON tbl_restaurant_area (menu_id, lower(name))"
            );
            statement.execute("ALTER TABLE tbl_restaurant_table ADD COLUMN IF NOT EXISTS area_id BIGINT");
            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_restaurant_table_area_id ON tbl_restaurant_table (area_id)"
            );
            log.info("Ensured restaurant area schema");
        } catch (Exception e) {
            throw new IllegalStateException("Restaurant area schema could not be initialized", e);
        }
    }
}
