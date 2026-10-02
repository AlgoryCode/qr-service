import psycopg2

PROD = dict(
    host="185.184.210.52", port=5432,
    dbname="algoryqrdb", user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable", connect_timeout=10
)

conn = psycopg2.connect(**PROD)
cur = conn.cursor()

print("=== tbl_fulfillment (user_id=1) ===")
cur.execute("""
    SELECT f.id, f.status, f.purchase_id, f.trial_log_id, f.expires_at::date, pp.code
    FROM tbl_fulfillment f
    LEFT JOIN tbl_plan_package pp ON pp.id=f.package_id
    WHERE f.user_id=1 ORDER BY f.id
""")
for r in cur.fetchall():
    print(" ", r)

print("\n=== chk_fulfillment_source constraint var mi? ===")
cur.execute("""
    SELECT conname FROM pg_constraint
    WHERE conrelid='tbl_fulfillment'::regclass AND conname='chk_fulfillment_source'
""")
print(" ", cur.fetchone())

print("\n=== tbl_fulfillment kolonlari ===")
cur.execute("SELECT column_name, is_nullable FROM information_schema.columns WHERE table_name='tbl_fulfillment' AND table_schema='public' ORDER BY ordinal_position")
for r in cur.fetchall():
    print(" ", r)

conn.close()
