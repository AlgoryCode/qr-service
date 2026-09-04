CREATE TABLE ai_menu_import_jobs (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    image_urls JSONB NOT NULL,
    extracted_products JSONB,
    ai_batch_id VARCHAR(128),
    ai_input_file_id VARCHAR(128),
    ai_output_file_id VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX idx_ai_menu_import_jobs_menu_status
    ON ai_menu_import_jobs (menu_id, status, created_at);

CREATE INDEX idx_ai_menu_import_jobs_status
    ON ai_menu_import_jobs (status, created_at);

CREATE TABLE ai_menu_import_drafts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES ai_menu_import_jobs (id),
    tenant_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    source_product_id VARCHAR(128) NOT NULL,
    product_data JSONB NOT NULL,
    confidence NUMERIC(5,4),
    approval_status VARCHAR(32) NOT NULL,
    approved_by BIGINT,
    approved_at TIMESTAMP,
    published_product_id BIGINT,
    reject_reason TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_menu_import_drafts_menu_status
    ON ai_menu_import_drafts (menu_id, approval_status, created_at);

CREATE UNIQUE INDEX uk_ai_menu_import_drafts_source
    ON ai_menu_import_drafts (job_id, source_product_id);
