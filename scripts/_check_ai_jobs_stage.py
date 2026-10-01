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

JOB = "dd09ccf8-816e-4376-8033-e21e51a9b826"
conn = psycopg2.connect(**STAGE)
cur = conn.cursor()

cur.execute(
    """
    SELECT id::text, status, error_code, left(coalesce(error_message,''), 200),
           model, attempt_count, created_at, updated_at, completed_at
    FROM smart_report_jobs
    ORDER BY created_at DESC
    LIMIT 15
    """
)
print("recent_ai_jobs")
for row in cur.fetchall():
    print(row)

cur.execute(
    """
    SELECT id::text, status, error_code, left(coalesce(error_message,''), 300),
           attempt_count, created_at, updated_at, completed_at
    FROM smart_report_jobs
    WHERE id::text = %s
    """,
    (JOB,),
)
print("target_job", cur.fetchall())

cur.execute(
    """
    SELECT e.id, e.job_id::text, e.level, left(e.message, 200), e.created_at
    FROM smart_report_events e
    WHERE e.job_id::text = %s
    ORDER BY e.created_at
    """,
    (JOB,),
)
print("target_events", cur.fetchall())

cur.execute("SELECT status, count(*) FROM smart_report_jobs GROUP BY status ORDER BY 1")
print("ai_status_counts", cur.fetchall())

conn.close()
