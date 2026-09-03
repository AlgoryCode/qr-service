CREATE TABLE integration_jobs (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    direction VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    snapshot_version INT NOT NULL,
    snapshot JSONB NOT NULL,
    external_store_id VARCHAR(128),
    ai_batch_id VARCHAR(128),
    ai_input_file_id VARCHAR(128),
    ai_output_file_id VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX idx_integration_jobs_menu_status
    ON integration_jobs (menu_id, status, created_at);

CREATE TABLE integration_pending_products (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES integration_jobs (id),
    tenant_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    source VARCHAR(32) NOT NULL,
    source_product_id VARCHAR(128),
    product_data JSONB NOT NULL,
    confidence NUMERIC(5,4),
    approval_status VARCHAR(32) NOT NULL,
    publish_targets JSONB NOT NULL,
    approved_by BIGINT,
    approved_at TIMESTAMP,
    published_product_id BIGINT,
    uber_item_id VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_integration_pending_products_menu_status
    ON integration_pending_products (menu_id, approval_status, created_at);

CREATE UNIQUE INDEX uk_integration_pending_products_source
    ON integration_pending_products (job_id, source_product_id);
