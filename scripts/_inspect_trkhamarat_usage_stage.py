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
cur.execute("SELECT id FROM tbl_user WHERE email = %s", (EMAIL,))
uid = cur.fetchone()[0]

cur.execute(
    """
    SELECT column_name FROM information_schema.columns
    WHERE table_name = 'tbl_fulfillment_usage_log'
    ORDER BY ordinal_position
    """
)
print("usage_cols", [r[0] for r in cur.fetchall()])

cur.execute(
    """
    SELECT *
    FROM tbl_fulfillment_usage_log
    WHERE user_id = %s
    ORDER BY created_at DESC
    LIMIT 30
    """,
    (uid,),
)
cols = [d[0] for d in cur.description]
print("usage_rows")
for row in cur.fetchall():
    print(dict(zip(cols, row)))

cur.execute(
    """
    SELECT id, fulfillment_id, feature_code, scope_code, quantity, used_quantity, unlimited, source, starts_at, expires_at
    FROM tbl_fulfillment_detail
    WHERE user_id = %s
      AND (
        feature_code ILIKE '%SMART_REPORT%'
        OR scope_code ILIKE '%SMART_REPORT%'
      )
    ORDER BY id DESC
    """,
    (uid,),
)
print("smart_report_details")
for row in cur.fetchall():
    print(row)

conn.close()
