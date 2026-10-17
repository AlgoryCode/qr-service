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
for pat in ("%ilhan%", "ilhanA", "%ilhanA%"):
    cur.execute(
        """
        SELECT id, email, first_name, last_name, provider
        FROM tbl_user
        WHERE email ILIKE %s OR first_name ILIKE %s OR last_name ILIKE %s
        """,
        (pat, pat, pat),
    )
    rows = cur.fetchall()
    if rows:
        print(pat, rows)

cur.execute(
    """
    SELECT column_name FROM information_schema.columns
    WHERE table_schema='public' AND table_name='demo_onboarding_assignment'
    ORDER BY ordinal_position
    """
)
print("demo_onboarding_assignment cols:", [r[0] for r in cur.fetchall()])
cur.execute("SELECT * FROM demo_onboarding_assignment WHERE assignment_key ILIKE '%ilhan%' OR user_id IN (25) LIMIT 10")
print("demo_onboarding:", cur.fetchall())

cur.execute(
    """
    SELECT table_name FROM information_schema.tables
    WHERE table_schema='public' AND table_name ILIKE '%auth%'
    ORDER BY 1
    """
)
print("auth tables:", [r[0] for r in cur.fetchall()])
conn.close()
