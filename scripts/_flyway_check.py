import psycopg2

conn = psycopg2.connect(
    host="185.184.210.52", port=5433,
    dbname="algoryqrdb-stage", user="postgres",
    password="postgres_stage", sslmode="disable", connect_timeout=8
)
cur = conn.cursor()

cur.execute("SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10")
rows = cur.fetchall()
for r in rows:
    print(r)

conn.close()
