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
conn.autocommit = False
cur = conn.cursor()

cur.execute("SELECT id FROM tbl_user WHERE email = %s", (EMAIL,))
uid = cur.fetchone()[0]
print("user_id", uid)

today = datetime.now(ZONE).date()
week_start = today - timedelta(days=today.weekday())
period_start = datetime.combine(week_start, datetime.min.time())
print("period_start", period_start)

cur.execute(
    """
    SELECT d.id, d.feature_code, d.scope_code, d.quantity, d.used_quantity, d.unlimited
    FROM tbl_fulfillment_detail d
    WHERE d.user_id = %s
      AND (
        d.feature_code = 'SMART_REPORTING'
        OR d.scope_code ILIKE '%%SMART_REPORT%%'
      )
    ORDER BY d.id
    """,
    (uid,),
)
details = cur.fetchall()
print("details_before")
for row in details:
    print(" ", row)
detail_ids = [row[0] for row in details]

if detail_ids:
    cur.execute(
        """
        SELECT id, detail_id, action, amount, reference_type, reference_id, created_at
        FROM tbl_fulfillment_usage_log
        WHERE user_id = %s
          AND detail_id = ANY(%s)
          AND created_at >= %s
        ORDER BY created_at DESC
        """,
        (uid, detail_ids, period_start),
    )
    logs = cur.fetchall()
    print("week_usage_logs", len(logs))
    for row in logs:
        print(" ", row)

    cur.execute(
        """
        DELETE FROM tbl_fulfillment_usage_log
        WHERE user_id = %s
          AND detail_id = ANY(%s)
          AND created_at >= %s
        """,
        (uid, detail_ids, period_start),
    )
    print("deleted_usage_logs", cur.rowcount)

    # restore consumed addon quantities for this week's consumes if any
    cur.execute(
        """
        UPDATE tbl_fulfillment_detail
        SET used_quantity = 0
        WHERE user_id = %s
          AND id = ANY(%s)
          AND COALESCE(unlimited, false) = false
          AND used_quantity > 0
          AND (
            feature_code = 'SMART_REPORTING'
            OR scope_code ILIKE '%%SMART_REPORT%%'
          )
        """,
        (uid, detail_ids),
    )
    print("reset_used_quantity_rows", cur.rowcount)

cur.execute(
    """
    DELETE FROM tbl_smart_report_events
    WHERE user_id = %s AND created_at >= %s
    """,
    (uid, period_start),
)
print("deleted_events", cur.rowcount)

conn.commit()

cur.execute(
    """
    SELECT COUNT(*) FROM tbl_smart_report_events
    WHERE user_id = %s AND created_at >= %s
    """,
    (uid, period_start),
)
print("events_after", cur.fetchone()[0])

if detail_ids:
    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_fulfillment_usage_log
        WHERE user_id = %s AND detail_id = ANY(%s) AND created_at >= %s
        """,
        (uid, detail_ids, period_start),
    )
    print("usage_after", cur.fetchone()[0])
    cur.execute(
        """
        SELECT id, feature_code, used_quantity, quantity
        FROM tbl_fulfillment_detail
        WHERE id = ANY(%s)
        """,
        (detail_ids,),
    )
    print("details_after")
    for row in cur.fetchall():
        print(" ", row)

conn.close()
print("DONE")
