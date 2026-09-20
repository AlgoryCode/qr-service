package com.ael.algoryqrservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class PrintAgentSchemaInitializer implements BeanPostProcessor {

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
                    "ALTER TABLE tbl_branch ADD COLUMN IF NOT EXISTS print_kitchen_enabled BOOLEAN NOT NULL DEFAULT FALSE"
            );
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tbl_print_agent_device (
                        id              BIGSERIAL PRIMARY KEY,
                        owner_user_id   BIGINT       NOT NULL,
                        branch_id       BIGINT       NOT NULL,
                        device_name     VARCHAR(128) NOT NULL,
                        device_token_hash VARCHAR(128) NOT NULL,
                        printer_name    VARCHAR(255),
                        agent_version   VARCHAR(64),
                        last_seen_at    TIMESTAMP,
                        enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
                        created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
                        updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
                        CONSTRAINT uk_print_agent_device_token UNIQUE (device_token_hash)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tbl_print_pairing_code (
                        id              BIGSERIAL PRIMARY KEY,
                        owner_user_id   BIGINT       NOT NULL,
                        branch_id       BIGINT       NOT NULL,
                        code_hash       VARCHAR(128) NOT NULL,
                        expires_at      TIMESTAMP    NOT NULL,
                        used_at         TIMESTAMP,
                        created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
                        CONSTRAINT uk_print_pairing_code_hash UNIQUE (code_hash)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tbl_print_job (
                        id                      BIGSERIAL PRIMARY KEY,
                        owner_user_id           BIGINT       NOT NULL,
                        branch_id               BIGINT,
                        device_id               BIGINT,
                        source_type             VARCHAR(32)  NOT NULL,
                        source_id               VARCHAR(64)  NOT NULL,
                        job_type                VARCHAR(32)  NOT NULL,
                        status                  VARCHAR(32)  NOT NULL,
                        payload_json            JSONB        NOT NULL,
                        idempotency_key         VARCHAR(160) NOT NULL,
                        attempts                INT          NOT NULL DEFAULT 0,
                        last_error              TEXT,
                        claimed_by_device_id    BIGINT,
                        claimed_at              TIMESTAMP,
                        completed_at            TIMESTAMP,
                        created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
                        updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
                        CONSTRAINT uk_print_job_idempotency UNIQUE (idempotency_key)
                    )
                    """);
            log.info("Ensured print agent schema");
        } catch (Exception e) {
            throw new IllegalStateException("Print agent schema could not be initialized", e);
        }
    }
}
