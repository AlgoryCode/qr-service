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

EMAIL = "trkhamarat@gmail.com"
ZONE = ZoneInfo("Europe/Istanbul")

conn = psycopg2.connect(**STAGE)
cur = conn.cursor()

cur.execute("SELECT id, email FROM tbl_user WHERE email = %s", (EMAIL,))
user = cur.fetchone()
print("user", user)
uid = user[0]

today = datetime.now(ZONE).date()
week_start = today - timedelta(days=today.weekday())
period_start = datetime.combine(week_start, datetime.min.time())
print("period_start", period_start)

cur.execute(
    """
    SELECT process_id::text, status, created_at, branch_id
    FROM tbl_smart_report_events
    WHERE user_id = %s
    ORDER BY created_at DESC
    LIMIT 20
    """,
    (uid,),
)
events = cur.fetchall()
print("all_recent_events", len(events))
for e in events:
    print(" ", e)

cur.execute(
    """
    SELECT COUNT(*) FROM tbl_smart_report_events
    WHERE user_id = %s AND created_at >= %s
    """,
    (uid, period_start),
)
print("used_this_week", cur.fetchone()[0])

# fulfillment details / usage for SMART_REPORTING
cur.execute(
    """
    SELECT column_name FROM information_schema.columns
    WHERE table_name = 'tbl_fulfillment_detail'
    ORDER BY ordinal_position
    """
)
print("fulfillment_detail_cols", [r[0] for r in cur.fetchall()])

cur.execute(
    """
    SELECT table_name FROM information_schema.tables
    WHERE table_schema='public' AND (
      table_name ILIKE '%fulfillment%usage%'
      OR table_name ILIKE '%usage_log%'
      OR table_name ILIKE '%fulfillment_detail%'
    )
    ORDER BY 1
    """
)
print("related_tables", cur.fetchall())

conn.close()
