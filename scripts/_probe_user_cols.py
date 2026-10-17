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
    """
    SELECT column_name, is_nullable, column_default
    FROM information_schema.columns
    WHERE table_name='tbl_user'
    ORDER BY ordinal_position
    """
)
for row in cur.fetchall():
    print(row)
cur.execute("SELECT * FROM tbl_user WHERE id=22 LIMIT 0")
print("sample cols", [d[0] for d in cur.description])
cur.close()
conn.close()
