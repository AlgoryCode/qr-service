-- Scheduled catalog changes: a product price change on a date, or a package discount
-- campaign for a window that reverts by itself.
CREATE TABLE IF NOT EXISTS catalog_schedule (
    id                     BIGSERIAL PRIMARY KEY,
    target_type            VARCHAR(16)   NOT NULL CHECK (target_type IN ('PRODUCT', 'PACKAGE')),
    target_id              BIGINT        NOT NULL,
    label                  VARCHAR(120)  NOT NULL,
    unit_price             NUMERIC(12,2),
    monthly_discount       NUMERIC(12,2),
    yearly_discount        NUMERIC(12,2),
    starts_at              TIMESTAMP     NOT NULL,
    ends_at                TIMESTAMP,
    status                 VARCHAR(16)   NOT NULL CHECK (status IN ('SCHEDULED', 'ACTIVE', 'DONE', 'CANCELLED', 'FAILED')),
    prev_unit_price        NUMERIC(12,2),
    prev_monthly_discount  NUMERIC(12,2),
    prev_yearly_discount   NUMERIC(12,2),
    last_error             VARCHAR(500),
    created_by             VARCHAR(128),
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    applied_at             TIMESTAMP,
    ended_at               TIMESTAMP,
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX IF NOT EXISTS idx_catalog_schedule_target ON catalog_schedule (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_catalog_schedule_status ON catalog_schedule (status, starts_at);

-- How an add-on module is presented in the member app's module store.
CREATE TABLE IF NOT EXISTS module_showcase (
    product_id   BIGINT       PRIMARY KEY REFERENCES tbl_product (id) ON DELETE CASCADE,
    tagline      VARCHAR(160),
    badge        VARCHAR(16)  CHECK (badge IN ('NEW', 'POPULAR', 'DEAL')),
    featured     BOOLEAN      NOT NULL DEFAULT FALSE,
    hidden       BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    updated_by   VARCHAR(128),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);
