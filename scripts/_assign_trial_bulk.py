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

TARGET_USERS = [
    (20, "reservationayaroof@gmail.com"),
    (21, "ulasbayram61@gmail.com"),
]

cur.execute("SELECT id FROM tbl_plan_package WHERE code='ULTIMATE_TRIAL_PACKAGE' AND active=TRUE LIMIT 1")
pkg_id = cur.fetchone()[0]
print(f"ULTIMATE_TRIAL_PACKAGE id={pkg_id}")

def assign_trial(user_id, email):
    print(f"\n--- {email} (id={user_id}) ---")

    cur.execute("UPDATE tbl_purchase SET status='EXPIRED' WHERE user_id=%s AND status='ACTIVE' AND purchase_type NOT IN ('ADD_ON')", (user_id,))
    print(f"  Mevcut aktif purchase'lar expire edildi")

    cur.execute("""
        SELECT id FROM tbl_fulfillment WHERE user_id=%s AND status='ACTIVE'
    """, (user_id,))
    for row in cur.fetchall():
        cur.execute("UPDATE tbl_fulfillment SET status='EXPIRED' WHERE id=%s", (row[0],))
    print(f"  Mevcut aktif fulfillment'lar expire edildi")

    cur.execute("""
        INSERT INTO tbl_purchase (
            user_id, package_id, package_code, package_name,
            status, purchase_type, payment_mode, payment_style,
            price, currency, billing_period,
            purchased_at, starts_at, expires_at,
            installment_count, system_managed, recurring_consent, refund_status,
            cancel_at_period_end
        ) VALUES (
            %s, %s, 'ULTIMATE_TRIAL_PACKAGE', 'Ultimate Deneme',
            'ACTIVE', 'SYSTEM_GRANT', 'THREE_DS', 'ONE_TIME',
            0.00, 'TRY', 'MONTHLY',
            NOW(), NOW(), NOW()+INTERVAL '15 days',
            1, false, false, 'NONE', false
        ) RETURNING id
    """, (user_id, pkg_id))
    purchase_id = cur.fetchone()[0]
    print(f"  Purchase olusturuldu: id={purchase_id}")

    cur.execute("""
        INSERT INTO tbl_fulfillment (user_id, purchase_id, package_id, status, starts_at, expires_at, created_at)
        VALUES (%s, %s, %s, 'ACTIVE', NOW(), NOW()+INTERVAL '15 days', NOW())
        RETURNING id
    """, (user_id, purchase_id, pkg_id))
    fulfillment_id = cur.fetchone()[0]
    print(f"  Fulfillment olusturuldu: id={fulfillment_id}")

    cur.execute("""
        INSERT INTO tbl_fulfillment_detail (
            fulfillment_id, user_id, product_id, product_type_id,
            feature_code, scope_code, quantity, unlimited, used_quantity,
            source, starts_at, expires_at, version, created_at
        )
        SELECT %(fid)s, %(uid)s, pr.id, 'PACKAGE_PRODUCT',
               COALESCE(NULLIF(TRIM(pr.feature_code),''), pr.code), pr.scope_code,
               CASE WHEN ppi.unlimited THEN 0 ELSE ppi.quantity END,
               ppi.unlimited, 0, 'ONBOARDING_PACKAGE',
               NOW(), NOW()+INTERVAL '15 days', 0, NOW()
        FROM tbl_plan_package_item ppi
        JOIN tbl_product pr ON pr.id = ppi.product_id
        WHERE ppi.package_id = %(pkg_id)s
    """, {"fid": fulfillment_id, "uid": user_id, "pkg_id": pkg_id})
    cur.execute("SELECT COUNT(*) FROM tbl_fulfillment_detail WHERE fulfillment_id=%s", (fulfillment_id,))
    print(f"  Fulfillment detail: {cur.fetchone()[0]} urun")

    cur.execute("""
        INSERT INTO tbl_trial_log (user_id, package_id, package_code, started_at, ends_at, duration_days, status, created_at)
        VALUES (%s, %s, 'ULTIMATE_TRIAL_PACKAGE', NOW(), NOW()+INTERVAL '15 days', 15, 'ACTIVE', NOW())
        ON CONFLICT ON CONSTRAINT uk_trial_log_user_id DO UPDATE
        SET package_id=EXCLUDED.package_id, package_code=EXCLUDED.package_code,
            started_at=EXCLUDED.started_at, ends_at=EXCLUDED.ends_at,
            duration_days=EXCLUDED.duration_days, status=EXCLUDED.status
    """, (user_id, pkg_id))
    print(f"  Trial log atandi")

for user_id, email in TARGET_USERS:
    assign_trial(user_id, email)

conn.commit()
print("\n=== TUM KULLANICILARA ULTIMATE_TRIAL_PACKAGE ATANDI ===")
conn.close()
