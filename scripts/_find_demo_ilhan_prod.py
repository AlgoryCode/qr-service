import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
    connect_timeout=15,
)
conn = psycopg2.connect(**PROD)
cur = conn.cursor()
cur.execute(
    """
    SELECT table_name FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name ILIKE '%demo%'
    ORDER BY 1
    """
)
print("demo tables:", [r[0] for r in cur.fetchall()])
for table in ("tbl_demo_accounts", "auth_demo_accounts", "demo_accounts"):
    cur.execute(
        "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name=%s",
        (table,),
    )
    if cur.fetchone():
        cur.execute(f"SELECT * FROM {table} WHERE username ILIKE '%ilhan%' LIMIT 5")
        print(table, cur.fetchall())
conn.close()
