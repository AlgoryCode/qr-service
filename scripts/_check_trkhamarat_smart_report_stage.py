import psycopg2
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

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
row = cur.fetchone()
uid = row[0]
print("user_id", uid)

tz = ZoneInfo("Europe/Istanbul")
today = datetime.now(tz).date()
week_start = today - timedelta(days=today.weekday())
period_start = datetime.combine(week_start, datetime.min.time())
print("period_start", period_start)

cur.execute(
    """
    SELECT process_id::text, status, created_at, updated_at, branch_id, menu_id, from_date, to_date
    FROM tbl_smart_report_events
    WHERE user_id=%s
    ORDER BY created_at DESC
    LIMIT 15
    """,
    (uid,),
)
events = cur.fetchall()
print("events", len(events))
for e in events:
    print(" EVENT", e)

process_ids = [e[0] for e in events]
if process_ids:
    cur.execute(
        """
        SELECT process_id::text, length(coalesce(result_text,'')), left(coalesce(result_text,''), 80), created_at
        FROM tbl_smart_report_results
        WHERE process_id = ANY(%s::uuid[])
        ORDER BY created_at DESC
        """,
        (process_ids,),
    )
    results = cur.fetchall()
    print("results", len(results))
    for r in results:
        print(" RESULT", r)

cur.execute(
    """
    SELECT count(*) FROM tbl_smart_report_events
    WHERE user_id=%s AND created_at >= %s
    """,
    (uid, period_start),
)
print("used_this_week", cur.fetchone()[0])

cur.execute(
    """
    SELECT id, product_code, created_at, meta
    FROM tbl_fulfillment_usage_log
    WHERE user_id=%s AND product_code ILIKE '%%SMART%%'
    ORDER BY created_at DESC
    LIMIT 10
    """,
    (uid,),
)
print("usage_logs")
for u in cur.fetchall():
    print(" USAGE", u)

conn.close()
