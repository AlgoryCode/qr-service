package com.ael.algoryqrservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Slf4j
public class StorefrontSchemaInitializer implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && "dataSource".equals(beanName)) {
            ensureSchema(dataSource);
        }
        return bean;
    }

    private void ensureSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            ensureMerchants(statement);
            ensureStoreOrders(statement);
            ensureStoreCouriers(statement);
            ensureCustomerAddresses(statement);
            log.info("Ensured storefront schema");
        } catch (Exception e) {
            throw new IllegalStateException("Storefront schema could not be initialized", e);
        }
    }

    private void ensureMerchants(Statement statement) throws Exception {
        statement.execute("CREATE SEQUENCE IF NOT EXISTS seq_merchant_store_no START WITH 10000 INCREMENT BY 1");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS merchants (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    branch_id BIGINT,
                    catalog_menu_id BIGINT NOT NULL,
                    store_no BIGINT NOT NULL,
                    slug VARCHAR(120) NOT NULL,
                    public_token VARCHAR(32) NOT NULL,
                    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
                    business_name VARCHAR(255) NOT NULL,
                    legal_name VARCHAR(255),
                    tax_office VARCHAR(120),
                    tax_number VARCHAR(32),
                    phone VARCHAR(32),
                    email VARCHAR(160),
                    logo_url VARCHAR(1024),
                    cover_url VARCHAR(1024),
                    address TEXT,
                    city VARCHAR(80),
                    district VARCHAR(80),
                    latitude NUMERIC(10, 7),
                    longitude NUMERIC(10, 7),
                    min_order_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
                    delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
                    free_delivery_threshold NUMERIC(12, 2),
                    avg_prep_minutes INT NOT NULL DEFAULT 30,
                    delivery_radius_km NUMERIC(6, 2),
                    delivery_types JSONB NOT NULL DEFAULT '["DELIVERY"]'::jsonb,
                    payment_methods JSONB NOT NULL DEFAULT '["CASH_ON_DELIVERY"]'::jsonb,
                    working_hours JSONB NOT NULL DEFAULT '[]'::jsonb,
                    manually_closed BOOLEAN NOT NULL DEFAULT FALSE,
                    order_counter BIGINT NOT NULL DEFAULT 0,
                    currency VARCHAR(8) NOT NULL DEFAULT 'TRY',
                    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_store_no ON merchants (store_no)");
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_public_token ON merchants (public_token)");
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_catalog_menu ON merchants (catalog_menu_id)");
        statement.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_user_active ON merchants (user_id) WHERE is_deleted = FALSE"
        );
        statement.execute("CREATE INDEX IF NOT EXISTS idx_merchant_branch ON merchants (branch_id)");
    }

    private void ensureStoreOrders(Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS store_orders (
                    id BIGSERIAL PRIMARY KEY,
                    merchant_id BIGINT NOT NULL,
                    order_no VARCHAR(32) NOT NULL,
                    public_token VARCHAR(32) NOT NULL,
                    customer_id BIGINT,
                    customer_name VARCHAR(160) NOT NULL,
                    customer_phone VARCHAR(32) NOT NULL,
                    delivery_type VARCHAR(16) NOT NULL,
                    address_text TEXT,
                    city VARCHAR(80),
                    district VARCHAR(80),
                    building_no VARCHAR(32),
                    floor_no VARCHAR(32),
                    door_no VARCHAR(32),
                    directions TEXT,
                    latitude NUMERIC(10, 7),
                    longitude NUMERIC(10, 7),
                    status VARCHAR(24) NOT NULL,
                    payment_method VARCHAR(32) NOT NULL,
                    payment_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    subtotal NUMERIC(12, 2) NOT NULL DEFAULT 0,
                    delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
                    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
                    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
                    currency VARCHAR(8) NOT NULL DEFAULT 'TRY',
                    note TEXT,
                    courier_id BIGINT,
                    assigned_at TIMESTAMP WITHOUT TIME ZONE,
                    confirmed_at TIMESTAMP WITHOUT TIME ZONE,
                    preparing_at TIMESTAMP WITHOUT TIME ZONE,
                    ready_at TIMESTAMP WITHOUT TIME ZONE,
                    dispatched_at TIMESTAMP WITHOUT TIME ZONE,
                    delivered_at TIMESTAMP WITHOUT TIME ZONE,
                    rejected_at TIMESTAMP WITHOUT TIME ZONE,
                    cancelled_at TIMESTAMP WITHOUT TIME ZONE,
                    reject_reason TEXT,
                    cancel_reason TEXT,
                    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
                )
                """);
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_store_order_public_token ON store_orders (public_token)");
        statement.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_store_order_merchant_no ON store_orders (merchant_id, order_no)"
        );
        statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_store_order_merchant_status ON store_orders (merchant_id, status)"
        );
        statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_store_order_merchant_created ON store_orders (merchant_id, created_at)"
        );
        statement.execute("CREATE INDEX IF NOT EXISTS idx_store_order_customer ON store_orders (customer_id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_store_order_courier ON store_orders (courier_id)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS store_order_items (
                    id BIGSERIAL PRIMARY KEY,
                    order_id BIGINT NOT NULL REFERENCES store_orders (id) ON DELETE CASCADE,
                    product_id BIGINT NOT NULL,
                    product_name VARCHAR(255) NOT NULL,
                    unit_price NUMERIC(12, 2) NOT NULL,
                    quantity INT NOT NULL,
                    note TEXT,
                    selected_options JSONB NOT NULL DEFAULT '[]'::jsonb,
                    line_total NUMERIC(12, 2) NOT NULL
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_store_order_item_order ON store_order_items (order_id)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS store_order_status_history (
                    id BIGSERIAL PRIMARY KEY,
                    order_id BIGINT NOT NULL REFERENCES store_orders (id) ON DELETE CASCADE,
                    from_status VARCHAR(24),
                    to_status VARCHAR(24) NOT NULL,
                    changed_by_type VARCHAR(16) NOT NULL,
                    changed_by_id BIGINT,
                    note TEXT,
                    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
                )
                """);
        statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_store_order_history_order ON store_order_status_history (order_id, created_at)"
        );
    }

    private void ensureStoreCouriers(Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS store_couriers (
                    id BIGSERIAL PRIMARY KEY,
                    merchant_id BIGINT NOT NULL,
                    full_name VARCHAR(160) NOT NULL,
                    phone VARCHAR(32) NOT NULL,
                    vehicle_type VARCHAR(24) NOT NULL DEFAULT 'MOTORCYCLE',
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_store_courier_merchant ON store_couriers (merchant_id, is_deleted)"
        );
    }

    private void ensureCustomerAddresses(Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS customer_addresses (
                    id BIGSERIAL PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    title VARCHAR(80) NOT NULL,
                    full_name VARCHAR(160),
                    phone VARCHAR(32),
                    address_text TEXT NOT NULL,
                    city VARCHAR(80),
                    district VARCHAR(80),
                    building_no VARCHAR(32),
                    floor_no VARCHAR(32),
                    door_no VARCHAR(32),
                    directions TEXT,
                    latitude NUMERIC(10, 7),
                    longitude NUMERIC(10, 7),
                    is_default BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_customer_address_customer ON customer_addresses (customer_id, is_deleted)"
        );
    }
}
