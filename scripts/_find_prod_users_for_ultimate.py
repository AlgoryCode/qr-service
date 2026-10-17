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

patterns = [
    "trkhamarat@gmail.com",
    "%ilhan%",
    "%tav%anc%",
    "%aytug%",
    "%aytuğ%",
]

print("=== tbl_user matches ===")
for pat in patterns:
    cur.execute(
        """
        SELECT id, email, first_name, last_name
        FROM tbl_user
        WHERE email ILIKE %s OR first_name ILIKE %s OR last_name ILIKE %s
        ORDER BY id
        """,
        (pat, pat, pat),
    )
    rows = cur.fetchall()
    if rows:
        print(f"-- pattern {pat!r} --")
        for row in rows:
            print(row)

print("\n=== menu business_name matches ===")
cur.execute(
    """
    SELECT m.user_id, m.business_name, u.email
    FROM tbl_menu m
    JOIN tbl_user u ON u.id = m.user_id
    WHERE m.business_name ILIKE ANY(ARRAY['%tav%anc%', '%ayt%', '%ilhan%'])
      AND COALESCE(m.is_deleted, FALSE) = FALSE
    ORDER BY m.user_id
    """
)
for row in cur.fetchall():
    print(row)

print("\n=== plan packages ===")
cur.execute(
    "SELECT id, code, validity_days FROM tbl_plan_package WHERE code IN ('ULTIMATE_PACKAGE', 'ULTIMATE_TRIAL_PACKAGE')"
)
for row in cur.fetchall():
    print(row)

print("\n=== active_package for candidates ===")
cur.execute(
    """
    SELECT u.id, u.email, ap.package_code, ap.status, ap.period_end
    FROM tbl_user u
    LEFT JOIN active_package ap ON ap.user_id = u.id
    WHERE lower(u.email) = lower('trkhamarat@gmail.com')
       OR u.email ILIKE '%ilhan%'
       OR EXISTS (
            SELECT 1 FROM tbl_menu m
            WHERE m.user_id = u.id AND m.business_name ILIKE '%tav%anc%'
       )
    ORDER BY u.id
    """
)
for row in cur.fetchall():
    print(row)

conn.close()
