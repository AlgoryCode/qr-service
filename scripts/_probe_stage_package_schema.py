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
cur.execute(
    """
    SELECT table_name FROM information_schema.tables
    WHERE table_schema='public'
      AND (
        table_name LIKE '%fulfillment%'
        OR table_name LIKE '%entitlement%'
        OR table_name LIKE '%purchase%'
        OR table_name = 'active_package'
      )
    ORDER BY 1
    """
)
print("tables:", [r[0] for r in cur.fetchall()])

for uid in (25, 13, 1):
    cur.execute("SELECT id, email FROM tbl_user WHERE id=%s", (uid,))
    print("user", cur.fetchone())

cur.execute(
    "SELECT column_name FROM information_schema.columns WHERE table_name='tbl_purchase' ORDER BY ordinal_position"
)
print("tbl_purchase cols:", [r[0] for r in cur.fetchall()[:20]], "...")

for table in ("active_package", "package_fulfillment", "user_entitlement", "tbl_purchase"):
    cur.execute(
        "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name=%s",
        (table,),
    )
    if not cur.fetchone():
        continue
    cur.execute(f"SELECT column_name FROM information_schema.columns WHERE table_name=%s ORDER BY ordinal_position", (table,))
    print(table, "cols:", [r[0] for r in cur.fetchall()])

for uid in (25, 13, 1):
    cur.execute("SELECT * FROM active_package WHERE user_id=%s", (uid,))
    print("active_package", uid, cur.fetchone())
    cur.execute(
        "SELECT id, package_code, status, expires_at FROM tbl_purchase WHERE user_id=%s ORDER BY id DESC LIMIT 2",
        (uid,),
    )
    print("purchase", uid, cur.fetchall())

cur.close()
conn.close()
