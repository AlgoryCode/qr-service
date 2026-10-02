import psycopg2
from datetime import datetime, timedelta

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
cur.execute("SELECT id FROM tbl_user WHERE email=%s", ("trkhamarat@gmail.com",))
uid = cur.fetchone()[0]
print("user_id", uid)

cur.execute(
    """
    SELECT process_id::text, status, created_at, updated_at, completed_at,
           branch_id, menu_id, from_date, to_date, error_code, error_message
    FROM tbl_smart_report_events
    WHERE user_id=%s
    ORDER BY created_at DESC
    LIMIT 10
    """,
    (uid,),
)
print("events:")
for r in cur.fetchall():
    print(r)

cur.execute(
    """
    SELECT process_id::text, created_at, left(coalesce(result_text,''), 80)
    FROM tbl_smart_report_results
    ORDER BY created_at DESC
    LIMIT 5
    """
)
print("recent_results:")
for r in cur.fetchall():
    print(r)

conn.close()
