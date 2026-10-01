import psycopg2

PROD = dict(
    host="185.184.210.52", port=5432,
    dbname="algoryqrdb", user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable", connect_timeout=10
)

conn = psycopg2.connect(**PROD)
cur = conn.cursor()

cur.execute("SELECT id FROM tbl_user WHERE email='trkhamarat@gmail.com'")
user_id = cur.fetchone()[0]
print(f"user_id={user_id}")

print("\n--- tbl_fulfillment ---")
cur.execute("""
    SELECT f.id, f.status, f.starts_at, f.expires_at, f.purchase_id, f.trial_log_id, pp.code, pp.name
    FROM tbl_fulfillment f
    LEFT JOIN tbl_plan_package pp ON pp.id = f.package_id
    WHERE f.user_id=%s
    ORDER BY f.id
""", (user_id,))
for r in cur.fetchall():
    print(r)

print("\n--- tbl_trial_log ---")
cur.execute("SELECT id, package_code, started_at, ends_at, status FROM tbl_trial_log WHERE user_id=%s", (user_id,))
for r in cur.fetchall():
    print(r)

print("\n--- tbl_purchase (aktif) ---")
cur.execute("""
    SELECT id, package_code, purchase_type, status, starts_at, expires_at
    FROM tbl_purchase WHERE user_id=%s AND status='ACTIVE'
""", (user_id,))
for r in cur.fetchall():
    print(r)

conn.close()
