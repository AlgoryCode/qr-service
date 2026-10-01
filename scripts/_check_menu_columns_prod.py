import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

# Columns referenced by Menu.java / QrBaseModel
EXPECTED = [
    "menu_id",
    "qr_id",
    "public_id",
    "user_id",
    "branch_id",
    "theme_id",
    "business_name",
    "slogan",
    "chef_name",
    "chef_avatar_key",
    "logo_url",
    "phone",
    "email",
    "address",
    "package_id",
    "active",
    "public_access_enabled",
    "public_access_disabled_reason",
    "rating_avg",
    "rating_count",
    "is_deleted",
    "created_at",
    "updated_at",
]

conn = psycopg2.connect(**PROD)
cur = conn.cursor()
cur.execute(
    """
    SELECT column_name
    FROM information_schema.columns
    WHERE table_schema='public' AND table_name='tbl_menu'
    """
)
existing = {r[0] for r in cur.fetchall()}
missing = [c for c in EXPECTED if c not in existing]
extra_interesting = sorted(existing - set(EXPECTED))
print("missing", missing)
print("other_cols_count", len(extra_interesting))
for c in missing:
    print(" MISSING", c)

# Can we select the entity-shaped row?
cols = ", ".join(c for c in EXPECTED if c in existing)
cur.execute(f"SELECT {cols} FROM tbl_menu WHERE qr_id=35")
row = cur.fetchone()
print("select_ok", row is not None)
print("public_access_enabled now", dict(zip([c for c in EXPECTED if c in existing], row)).get("public_access_enabled"))
cur.close()
conn.close()
