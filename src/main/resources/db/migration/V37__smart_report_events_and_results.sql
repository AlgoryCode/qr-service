ALTER TABLE tbl_smart_report_job RENAME TO tbl_smart_report_events;

ALTER TABLE tbl_smart_report_events RENAME COLUMN job_id TO process_id;

ALTER INDEX IF EXISTS idx_smart_report_job_user_created
    RENAME TO idx_smart_report_events_user_created;

ALTER INDEX IF EXISTS idx_smart_report_job_notify_pending
    RENAME TO idx_smart_report_events_notify_pending;

CREATE INDEX IF NOT EXISTS idx_smart_report_events_user_status_created
    ON tbl_smart_report_events (user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_smart_report_events_menu_status
    ON tbl_smart_report_events (menu_id, status);

CREATE TABLE IF NOT EXISTS tbl_smart_report_results (
    id           BIGSERIAL PRIMARY KEY,
    menu_id      BIGINT       NOT NULL,
    process_id   UUID         NOT NULL UNIQUE
        REFERENCES tbl_smart_report_events (process_id) ON DELETE CASCADE,
    result_text  TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_smart_report_results_menu_id
    ON tbl_smart_report_results (menu_id);

INSERT INTO tbl_smart_report_results (menu_id, process_id, result_text, created_at)
SELECT
    e.menu_id,
    e.process_id,
    COALESCE(
        NULLIF(e.result_json ->> 'rawMarkdown', ''),
        e.result_json::text
    ),
    COALESCE(e.completed_at, e.created_at, NOW())
FROM tbl_smart_report_events e
WHERE e.result_json IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM tbl_smart_report_results r
      WHERE r.process_id = e.process_id
  );

ALTER TABLE tbl_smart_report_events DROP COLUMN IF EXISTS result_json;

ALTER TABLE tbl_user_entitlement
    ADD COLUMN IF NOT EXISTS last_usage TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_user_entitlement_product_last_usage
    ON tbl_user_entitlement (user_id, product_code, last_usage DESC);
