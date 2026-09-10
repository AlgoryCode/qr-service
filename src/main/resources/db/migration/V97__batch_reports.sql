CREATE TABLE tbl_batch_reports (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    openai_batch_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_total INT NOT NULL DEFAULT 0,
    request_completed INT NOT NULL DEFAULT 0,
    request_failed INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE UNIQUE INDEX uk_batch_reports_openai_batch_id
    ON tbl_batch_reports (openai_batch_id);

CREATE INDEX idx_batch_reports_user_created
    ON tbl_batch_reports (user_id, created_at DESC);

CREATE INDEX idx_batch_reports_status_updated
    ON tbl_batch_reports (status, updated_at);

CREATE TABLE tbl_batch_report_items (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES tbl_batch_reports (id),
    status VARCHAR(32) NOT NULL,
    payload_json JSONB NOT NULL,
    result_json JSONB,
    error_message TEXT,
    branch_id BIGINT,
    branch_name VARCHAR(255),
    menu_id BIGINT,
    menu_name VARCHAR(255),
    from_date DATE,
    to_date DATE,
    locale VARCHAR(16),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_batch_report_items_batch
    ON tbl_batch_report_items (batch_id);
