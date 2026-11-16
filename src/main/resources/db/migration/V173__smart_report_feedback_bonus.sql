-- Giving feedback on a report earns one extra smart report credit, at most once per week.
-- bonus_week is the Monday of the week the credit was earned in; bonus_used_at is set when a report spends it.
ALTER TABLE tbl_smart_report_feedback ADD COLUMN IF NOT EXISTS bonus_week DATE;
ALTER TABLE tbl_smart_report_feedback ADD COLUMN IF NOT EXISTS bonus_used_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_smart_report_feedback_bonus_week
    ON tbl_smart_report_feedback (merchant_id, bonus_week)
    WHERE bonus_week IS NOT NULL;
