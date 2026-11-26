-- Cancels a console session that was left open by mistake (no charge, hidden from reports).
-- Run: psql -h <host> -U postgres -d algoryqrdb -f cancel-console-session.sql
--
-- The session is closed at its own start time (0 minutes, 0 amount) and soft-deleted, so it
-- frees the console for a new session and does not appear in revenue or history.
\set ON_ERROR_STOP on
\pset pager off

\prompt 'Branch name contains (e.g. tav): ' branch_q

SELECT s.id AS session_id, b.name AS branch, c.name AS console, s.customer_name,
       s.started_at, s.paused_at, s.hourly_rate, s.merchant_id, s.branch_id
FROM tbl_console_session s
JOIN tbl_branch b ON b.id = s.branch_id
JOIN tbl_game_console c ON c.id = s.console_id
WHERE s.ended_at IS NULL AND s.is_deleted = FALSE
  AND b.name ILIKE '%' || :'branch_q' || '%'
ORDER BY s.started_at;

\prompt 'session_id to cancel: ' sid

BEGIN;

UPDATE tbl_device_session_segment
SET ended_at = started_at
WHERE session_id = :'sid'::bigint AND ended_at IS NULL;

UPDATE tbl_console_session
SET ended_at = started_at,
    paused_at = NULL,
    duration_minutes = 0,
    amount = 0,
    time_amount = 0,
    items_amount = 0,
    gross_amount = 0,
    discount_amount = 0,
    note = left(concat_ws(' ', note, '[Açık kalan oturum DB üzerinden iptal edildi]'), 500),
    is_deleted = TRUE,
    updated_at = now()
WHERE id = :'sid'::bigint AND ended_at IS NULL AND is_deleted = FALSE
RETURNING id, console_id, started_at, ended_at, amount, is_deleted;

\prompt 'One row above? Type YES to commit, anything else rolls back: ' ok
SELECT (:'ok' = 'YES') AS do_commit \gset
\if :do_commit
COMMIT;
\echo 'Cancelled. The console is free for a new session.'
\else
ROLLBACK;
\echo 'Rolled back, nothing changed.'
\endif
