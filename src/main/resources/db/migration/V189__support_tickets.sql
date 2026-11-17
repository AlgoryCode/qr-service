-- Platform feedback becomes a support ticket: priority, owner, response times and a thread.
ALTER TABLE tbl_platform_feedback ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE tbl_platform_feedback ADD COLUMN IF NOT EXISTS assignee_id BIGINT;
ALTER TABLE tbl_platform_feedback ADD COLUMN IF NOT EXISTS assignee_name VARCHAR(128);
ALTER TABLE tbl_platform_feedback ADD COLUMN IF NOT EXISTS first_response_at TIMESTAMP;
ALTER TABLE tbl_platform_feedback ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;
ALTER TABLE tbl_platform_feedback DROP CONSTRAINT IF EXISTS tbl_platform_feedback_priority_check;
ALTER TABLE tbl_platform_feedback ADD CONSTRAINT tbl_platform_feedback_priority_check
    CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));

CREATE TABLE IF NOT EXISTS platform_feedback_message (
    id           BIGSERIAL PRIMARY KEY,
    feedback_id  BIGINT        NOT NULL REFERENCES tbl_platform_feedback (id) ON DELETE CASCADE,
    author_name  VARCHAR(128),
    body         TEXT          NOT NULL,
    internal     BOOLEAN       NOT NULL DEFAULT FALSE,
    emailed      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_platform_feedback_message_feedback ON platform_feedback_message (feedback_id, created_at);
