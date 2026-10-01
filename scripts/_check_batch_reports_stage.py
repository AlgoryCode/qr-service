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
cur.execute("SELECT id FROM tbl_user WHERE email=%s", ("trkhamarat@gmail.com",))
uid = cur.fetchone()[0]
print("user_id", uid)

cur.execute(
    """
    SELECT id::text, openai_batch_id, status, request_total, request_completed, request_failed,
           created_at, updated_at, completed_at, error_message
    FROM tbl_batch_reports
    WHERE user_id=%s
    ORDER BY created_at DESC
    LIMIT 10
    """,
    (uid,),
)
batches = cur.fetchall()
print("batches", len(batches))
for b in batches:
    print(" BATCH", b)

cur.execute(
    """
    SELECT i.id::text, i.batch_id::text, i.status, length(coalesce(i.result_json::text,'')),
           left(coalesce(i.error_message,''), 120), i.created_at, i.updated_at
    FROM tbl_batch_report_items i
    JOIN tbl_batch_reports b ON b.id = i.batch_id
    WHERE b.user_id=%s
    ORDER BY i.created_at DESC
    LIMIT 20
    """,
    (uid,),
)
items = cur.fetchall()
print("items", len(items))
for i in items:
    print(" ITEM", i)

cur.execute(
    """
    SELECT process_id::text, status, created_at, updated_at, completed_at, left(coalesce(error_message,''),120)
    FROM tbl_smart_report_events
    WHERE user_id=%s
    ORDER BY created_at DESC
    LIMIT 10
    """,
    (uid,),
)
events = cur.fetchall()
print("events", len(events))
for e in events:
    print(" EVENT", e)

pids = [e[0] for e in events]
if pids:
    cur.execute(
        """
        SELECT process_id::text, length(result_text), left(result_text, 80), created_at
        FROM tbl_smart_report_results
        WHERE process_id = ANY(%s::uuid[])
        """,
        (pids,),
    )
    print("results", cur.fetchall())

conn.close()
