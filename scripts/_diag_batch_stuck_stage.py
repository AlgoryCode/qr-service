import psycopg2

conn = psycopg2.connect(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=10,
)
cur = conn.cursor()
cur.execute(
    """
    SELECT column_name, is_nullable
    FROM information_schema.columns
    WHERE table_name = 'tbl_smart_report_results'
    ORDER BY ordinal_position
    """
)
print("results_cols", cur.fetchall())
cur.execute(
    """
    SELECT id::text, openai_batch_id, status, request_total, request_completed,
           request_failed, updated_at, completed_at
    FROM tbl_batch_reports
    ORDER BY created_at DESC
    LIMIT 5
    """
)
print("batches", cur.fetchall())
cur.execute(
    """
    SELECT process_id::text, status, menu_id, branch_id, updated_at, completed_at
    FROM tbl_smart_report_events
    WHERE user_id = 22
    ORDER BY created_at DESC
    LIMIT 5
    """
)
print("events", cur.fetchall())
conn.close()
