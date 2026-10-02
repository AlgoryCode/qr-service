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
    SELECT table_name FROM information_schema.tables
    WHERE table_schema='public'
      AND (table_name ILIKE '%smart%report%' OR table_name ILIKE '%ai%job%')
    ORDER BY 1
    """
)
print("tables", cur.fetchall())
cur.execute("SELECT status, count(*) FROM tbl_smart_report_events GROUP BY status ORDER BY 1")
print("status_counts", cur.fetchall())
cur.execute(
    """
    SELECT process_id::text, user_id, status, created_at, updated_at,
           EXTRACT(EPOCH FROM (NOW() - created_at))/60 AS age_min
    FROM tbl_smart_report_events
    WHERE status IN ('queued','processing')
    ORDER BY created_at DESC
    LIMIT 20
    """
)
print("stuck:")
for row in cur.fetchall():
    print(row)
conn.close()
