import psycopg2

PROD = dict(
    host="185.184.210.52", port=5432,
    dbname="algoryqrdb", user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable", connect_timeout=10
)

conn = psycopg2.connect(**PROD)
conn.autocommit = False
cur = conn.cursor()

USER_ID = 1

cur.execute("""
    UPDATE tbl_purchase
    SET status='EXPIRED'
    WHERE id=136 AND user_id=%s
""", (USER_ID,))
print("Purchase #136 EXPIRED yapildi")

cur.execute("""
    UPDATE tbl_fulfillment
    SET status='EXPIRED'
    WHERE id=4 AND user_id=%s
""", (USER_ID,))
print("Fulfillment #4 (ULTIMATE_PACKAGE) EXPIRED yapildi")

cur.execute("""
    UPDATE tbl_fulfillment_detail
    SET expires_at=NOW()
    WHERE fulfillment_id=4
""")
print("Fulfillment #4 detail'lari expire edildi")

cur.execute("""
    UPDATE tbl_trial_log
    SET started_at=NOW(), ends_at=NOW()+INTERVAL '15 days', status='ACTIVE', created_at=NOW()
    WHERE id=8 AND user_id=%s
""", (USER_ID,))
print("Trial log #8 tazelenildi (15 gun)")

cur.execute("""
    UPDATE tbl_fulfillment
    SET status='ACTIVE', starts_at=NOW(), expires_at=NOW()+INTERVAL '15 days'
    WHERE id=12 AND user_id=%s
""", (USER_ID,))
print("Fulfillment #12 (ULTIMATE_TRIAL_PACKAGE) ACTIVE ve tazelendi")

cur.execute("""
    UPDATE tbl_fulfillment_detail
    SET starts_at=NOW(), expires_at=NOW()+INTERVAL '15 days'
    WHERE fulfillment_id=12
""")
print("Fulfillment #12 detail'lari tazelendi")

conn.commit()

cur.execute("""
    SELECT f.id, f.status, f.starts_at::date, f.expires_at::date, pp.code
    FROM tbl_fulfillment f
    JOIN tbl_plan_package pp ON pp.id=f.package_id
    WHERE f.user_id=%s ORDER BY f.id
""", (USER_ID,))
print("\n--- GUNCEL FULFILLMENT ---")
for r in cur.fetchall():
    print(" ", r)

conn.close()
print("\nTAMAM — trkhamarat@gmail.com artik sadece 15 gunluk trial'a sahip")
