import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=15,
)

conn = psycopg2.connect(**STAGE)
cur = conn.cursor()
for pattern in ("%ilhan%", "%a11_fen%", "%tavsan%", "%trkhamarat%", "ilhanA"):
    cur.execute(
        """
        SELECT id, email, first_name, last_name
        FROM tbl_user
        WHERE email ILIKE %s OR first_name ILIKE %s OR last_name ILIKE %s
        LIMIT 20
        """,
        (pattern, pattern, pattern),
    )
    rows = cur.fetchall()
    if rows:
        print(pattern, rows)

cur.execute("SELECT id, email FROM tbl_user ORDER BY id LIMIT 30")
print("first users", cur.fetchall())

cur.close()
conn.close()
