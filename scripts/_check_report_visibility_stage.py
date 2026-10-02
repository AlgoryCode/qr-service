import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=10,
)

conn = psycopg2.connect(**STAGE)
cur = conn.cursor()

cur.execute(
    """
    SELECT process_id::text, status, error_code, left(coalesce(error_message,''), 200),
           created_at, updated_at, completed_at, branch_id, menu_id
    FROM tbl_smart_report_events
    WHERE user_id = 22
    ORDER BY created_at DESC
    LIMIT 10
    """
)
print("=== qr events ===")
for row in cur.fetchall():
    print(row)

cur.execute(
    """
    SELECT r.process_id::text, r.created_at, length(coalesce(r.result_text,'')),
           left(coalesce(r.result_text,''), 120)
    FROM tbl_smart_report_results r
    JOIN tbl_smart_report_events e ON e.process_id = r.process_id
    WHERE e.user_id = 22
    ORDER BY r.created_at DESC
    LIMIT 10
    """
)
print("=== results for user ===")
for row in cur.fetchall():
    print(row)

cur.execute(
    """
    SELECT process_id::text, created_at, length(coalesce(result_text,''))
    FROM tbl_smart_report_results
    ORDER BY created_at DESC
    LIMIT 5
    """
)
print("=== latest results any user ===")
for row in cur.fetchall():
    print(row)

cur.execute("SELECT status, count(*) FROM tbl_smart_report_events GROUP BY 1 ORDER BY 1")
print("=== status counts ===", cur.fetchall())

conn.close()
