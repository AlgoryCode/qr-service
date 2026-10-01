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

for table in ["smart_report_jobs", "smart_report_events"]:
    cur.execute(
        """
        SELECT column_name FROM information_schema.columns
        WHERE table_name=%s ORDER BY ordinal_position
        """,
        (table,),
    )
    print(table, "cols", [r[0] for r in cur.fetchall()])

cur.execute("SELECT * FROM smart_report_jobs ORDER BY created_at DESC NULLS LAST LIMIT 10")
cols = [d[0] for d in cur.description]
print("jobs:")
for row in cur.fetchall():
    print(dict(zip(cols, row)))

cur.execute("SELECT * FROM smart_report_events ORDER BY created_at DESC NULLS LAST LIMIT 20")
cols = [d[0] for d in cur.description]
print("ai_events:")
for row in cur.fetchall():
    print(dict(zip(cols, row)))

cur.execute(
    "SELECT * FROM smart_report_jobs WHERE id::text = %s OR id::text LIKE %s",
    (JOB, JOB[:8] + "%"),
)
print("job_match", cur.fetchall())

conn.close()
