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

JOB = "400c3d3a-8b91-4215-8acc-6fc4f3a42086"
conn = psycopg2.connect(**STAGE)
cur = conn.cursor()
cur.execute(
    """
    SELECT process_id::text, status, error_code, left(coalesce(error_message,''), 300),
           created_at, updated_at, completed_at, branch_id
    FROM tbl_smart_report_events
    WHERE process_id = %s
    """,
    (JOB,),
)
print("event", cur.fetchone())
cur.execute(
    """
    SELECT process_id::text, left(coalesce(result_text,''), 80), created_at
    FROM tbl_smart_report_results
    WHERE process_id = %s
    """,
    (JOB,),
)
print("result", cur.fetchone())
cur.execute(
    """
    SELECT process_id::text, status, created_at, updated_at
    FROM tbl_smart_report_events
    WHERE user_id = 22
    ORDER BY created_at DESC
    LIMIT 5
    """
)
print("recent")
for row in cur.fetchall():
    print(row)
conn.close()
