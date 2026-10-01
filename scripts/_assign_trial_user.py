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

EMAIL = "trkhamarat@gmail.com"

cur.execute("SELECT id, email FROM tbl_user WHERE email = %s", (EMAIL,))
user = cur.fetchone()
if not user:
    print(f"KULLANICI BULUNAMADI: {EMAIL}")
    conn.close()
    exit(1)

user_id = user[0]
print(f"Kullanici bulundu: id={user_id}, email={user[1]}")

cur.execute("SELECT id FROM tbl_plan_package WHERE code='ULTIMATE_TRIAL_PACKAGE' AND active=TRUE LIMIT 1")
pkg = cur.fetchone()
if not pkg:
    print("ULTIMATE_TRIAL_PACKAGE bulunamadi!")
    conn.close()
    exit(1)
pkg_id = pkg[0]
print(f"ULTIMATE_TRIAL_PACKAGE id={pkg_id}")

cur.execute("SELECT id, status, ends_at FROM tbl_trial_log WHERE user_id=%s", (user_id,))
existing = cur.fetchone()
if existing:
    print(f"Mevcut trial log: id={existing[0]}, status={existing[1]}, ends_at={existing[2]}")
    cur.execute("""
        UPDATE tbl_trial_log
        SET package_id=%s, package_code='ULTIMATE_TRIAL_PACKAGE',
            started_at=NOW(), ends_at=NOW()+INTERVAL '15 days',
            duration_days=15, status='ACTIVE', created_at=NOW()
        WHERE user_id=%s
    """, (pkg_id, user_id))
    print("Trial log ACTIVE olarak guncellendi.")
    trial_log_id = existing[0]
else:
    cur.execute("""
        INSERT INTO tbl_trial_log (user_id,package_id,package_code,started_at,ends_at,duration_days,status,created_at)
        VALUES (%s,%s,'ULTIMATE_TRIAL_PACKAGE',NOW(),NOW()+INTERVAL '15 days',15,'ACTIVE',NOW())
        RETURNING id
    """, (user_id, pkg_id))
    trial_log_id = cur.fetchone()[0]
    print(f"Yeni trial log olusturuldu: id={trial_log_id}")

cur.execute("SELECT id FROM tbl_fulfillment WHERE trial_log_id=%s", (trial_log_id,))
fulfillment = cur.fetchone()
if fulfillment:
    fulfillment_id = fulfillment[0]
    cur.execute("""
        UPDATE tbl_fulfillment
        SET status='ACTIVE', starts_at=NOW(), expires_at=NOW()+INTERVAL '15 days'
        WHERE id=%s
    """, (fulfillment_id,))
    print(f"Mevcut fulfillment guncellendi: id={fulfillment_id}")
else:
    cur.execute("""
        INSERT INTO tbl_fulfillment (user_id,trial_log_id,package_id,status,starts_at,expires_at,created_at)
        VALUES (%s,%s,%s,'ACTIVE',NOW(),NOW()+INTERVAL '15 days',NOW())
        RETURNING id
    """, (user_id, trial_log_id, pkg_id))
    fulfillment_id = cur.fetchone()[0]
    print(f"Yeni fulfillment olusturuldu: id={fulfillment_id}")

cur.execute("DELETE FROM tbl_fulfillment_detail WHERE fulfillment_id=%s", (fulfillment_id,))
cur.execute("""
    INSERT INTO tbl_fulfillment_detail (fulfillment_id,user_id,product_id,product_type_id,feature_code,scope_code,quantity,unlimited,used_quantity,source,starts_at,expires_at,version,created_at)
    SELECT %(fid)s, %(uid)s, pr.id,'PACKAGE_PRODUCT',
           COALESCE(NULLIF(TRIM(pr.feature_code),''),pr.code),pr.scope_code,
           CASE WHEN ppi.unlimited THEN 0 ELSE ppi.quantity END,ppi.unlimited,0,
           'ONBOARDING_PACKAGE',NOW(),NOW()+INTERVAL '15 days',0,NOW()
    FROM tbl_plan_package_item ppi
    JOIN tbl_product pr ON pr.id=ppi.product_id
    WHERE ppi.package_id=%(pkg_id)s
""", {"fid": fulfillment_id, "uid": user_id, "pkg_id": pkg_id})

cur.execute("SELECT COUNT(*) FROM tbl_fulfillment_detail WHERE fulfillment_id=%s", (fulfillment_id,))
detail_count = cur.fetchone()[0]
print(f"Fulfillment detail satirlari: {detail_count}")

conn.commit()
print(f"\n=== BASARILI ===")
print(f"  {EMAIL} -> ULTIMATE_TRIAL_PACKAGE (15 gun) atandi")
conn.close()
