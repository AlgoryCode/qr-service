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

def col_exists(table, col):
    cur.execute(
        "SELECT 1 FROM information_schema.columns "
        "WHERE table_schema='public' AND table_name=%s AND column_name=%s",
        (table, col)
    )
    return cur.fetchone() is not None

def table_exists(table):
    cur.execute(
        "SELECT 1 FROM information_schema.tables "
        "WHERE table_schema='public' AND table_name=%s",
        (table,)
    )
    return cur.fetchone() is not None

def index_exists(name):
    cur.execute("SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname=%s", (name,))
    return cur.fetchone() is not None

def constraint_exists(table, name):
    cur.execute(
        "SELECT 1 FROM information_schema.table_constraints "
        "WHERE table_schema='public' AND table_name=%s AND constraint_name=%s",
        (table, name)
    )
    return cur.fetchone() is not None

def flyway_applied(version):
    cur.execute(
        "SELECT 1 FROM flyway_schema_history WHERE version=%s AND success=TRUE",
        (version,)
    )
    return cur.fetchone() is not None

def record_flyway(version, description, script, checksum):
    cur.execute("SELECT COALESCE(MAX(installed_rank),0)+1 FROM flyway_schema_history")
    rank = cur.fetchone()[0]
    cur.execute(
        "INSERT INTO flyway_schema_history "
        "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) "
        "VALUES (%s,%s,%s,'SQL',%s,%s,'postgres',NOW(),0,TRUE) "
        "ON CONFLICT DO NOTHING",
        (rank, version, description, script, checksum)
    )

print("=== PROD FLYWAY DURUMU ===")
cur.execute("SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 15")
for r in cur.fetchall():
    print(" ", r)

print("\n=== EKSIK KOLON KONTROL ===")
missing = []
checks = [
    ("tbl_campaign", "image_url"),
    ("tbl_campaign", "cover_image_url"),
    ("tbl_menu", "public_id"),
    ("tbl_customer", "phone"),
    ("tbl_fulfillment", "trial_log_id"),
    ("tbl_plan_package", "trial_days"),
    ("tbl_plan_package", "trial_eligible"),
]
for table, col in checks:
    exists = col_exists(table, col)
    status = "OK" if exists else "EKSIK"
    print(f"  {table}.{col}: {status}")
    if not exists:
        missing.append((table, col))

print("\n=== TABLE KONTROL ===")
for t in ["tbl_trial_log", "tbl_menu_product_option", "tbl_menu_product_option_group", "tbl_purchase_log"]:
    print(f"  {t}: {'VAR' if table_exists(t) else 'YOK'}")

conn.close()
print("\nDONE")
