-- Which credit paid for each smart report: FREE (weekly free quota), BONUS (earned by giving feedback) or PAID (purchased).
ALTER TABLE tbl_smart_report_events ADD COLUMN IF NOT EXISTS credit_source VARCHAR(16);

-- Backfill: the quota has been one free report per week, so a merchant's first report of each week was free
-- and any further report that week spent a purchased credit.
WITH ranked AS (
    SELECT process_id,
           ROW_NUMBER() OVER (
               PARTITION BY merchant_id, date_trunc('week', created_at)
               ORDER BY created_at, process_id
           ) AS position
    FROM tbl_smart_report_events
    WHERE credit_source IS NULL
)
UPDATE tbl_smart_report_events e
SET credit_source = CASE WHEN ranked.position = 1 THEN 'FREE' ELSE 'PAID' END
FROM ranked
WHERE e.process_id = ranked.process_id;

CREATE INDEX IF NOT EXISTS idx_smart_report_events_merchant_created
    ON tbl_smart_report_events (merchant_id, created_at);
