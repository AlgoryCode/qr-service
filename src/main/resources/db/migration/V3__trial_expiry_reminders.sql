CREATE TABLE IF NOT EXISTS tbl_purchase_reminder (
    id BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    reminder_type VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_reminder_purchase
        FOREIGN KEY (purchase_id) REFERENCES tbl_purchase (id),
    CONSTRAINT uk_purchase_reminder_purchase_type
        UNIQUE (purchase_id, reminder_type),
    CONSTRAINT uk_purchase_reminder_event
        UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_purchase_reminder_purchase
    ON tbl_purchase_reminder (purchase_id);
