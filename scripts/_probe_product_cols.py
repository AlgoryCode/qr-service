import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)
conn = psycopg2.connect(**STAGE)
cur = conn.cursor()
cur.execute(
    "SELECT column_name FROM information_schema.columns WHERE table_name='tbl_product' ORDER BY ordinal_position"
)
print([r[0] for r in cur.fetchall()])
cur.close()
conn.close()
