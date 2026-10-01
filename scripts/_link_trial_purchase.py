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

cur.execute("SELECT id, code, name FROM tbl_plan_package WHERE code='ULTIMATE_TRIAL_PACKAGE' AND active=TRUE LIMIT 1")
pkg = cur.fetchone()
pkg_id, pkg_code, pkg_name = pkg[0], pkg[1], pkg[2]
print(f"Package: id={pkg_id}, code={pkg_code}, name={pkg_name}")

cur.execute("""
    SELECT id FROM tbl_purchase
    WHERE user_id=%s AND package_code='ULTIMATE_TRIAL_PACKAGE' AND status='ACTIVE'
    LIMIT 1
""", (USER_ID,))
existing_purchase = cur.fetchone()

if existing_purchase:
    purchase_id = existing_purchase[0]
    cur.execute("""
        UPDATE tbl_purchase
        SET expires_at=NOW()+INTERVAL '15 days', starts_at=NOW()
        WHERE id=%s
    """, (purchase_id,))
    print(f"Mevcut TRIAL purchase guncellendi: id={purchase_id}")
else:
    cur.execute("""
        INSERT INTO tbl_purchase (
            user_id, package_id, package_code, package_name,
            status, purchase_type, payment_mode, payment_style,
            price, currency, billing_period,
            purchased_at, starts_at, expires_at,
            installment_count, system_managed, recurring_consent, refund_status,
            cancel_at_period_end
        ) VALUES (
            %s, %s, %s, %s,
            'ACTIVE', 'TRIAL', 'THREE_DS', 'ONE_TIME',
            0.00, 'TRY', 'MONTHLY',
            NOW(), NOW(), NOW()+INTERVAL '15 days',
            1, false, false, 'NONE',
            false
        ) RETURNING id
    """, (USER_ID, pkg_id, pkg_code, pkg_name))
    purchase_id = cur.fetchone()[0]
    print(f"Yeni TRIAL purchase olusturuldu: id={purchase_id}")

cur.execute("""
    UPDATE tbl_fulfillment
    SET purchase_id=%s, trial_log_id=NULL
    WHERE id=12 AND user_id=%s
""", (purchase_id, USER_ID))
print(f"Fulfillment #12 -> purchase_id={purchase_id}, trial_log_id=NULL")

cur.execute("""
    UPDATE tbl_trial_log
    SET started_at=NOW(), ends_at=NOW()+INTERVAL '15 days', status='ACTIVE'
    WHERE user_id=%s
""", (USER_ID,))
print("Trial log tazelendi")

conn.commit()

cur.execute("""
    SELECT f.id, f.status, f.purchase_id, f.trial_log_id, f.expires_at::date, pp.code, pp.name
    FROM tbl_fulfillment f
    JOIN tbl_plan_package pp ON pp.id=f.package_id
    WHERE f.user_id=%s ORDER BY f.id
""", (USER_ID,))
print("\n--- GUNCEL FULFILLMENT ---")
for r in cur.fetchall():
    print(" ", r)

cur.execute("SELECT id, status, purchase_type, expires_at::date FROM tbl_purchase WHERE user_id=%s AND status='ACTIVE'", (USER_ID,))
print("\n--- AKTIF PURCHASE ---")
for r in cur.fetchall():
    print(" ", r)

conn.close()
print("\nTAMAM")
