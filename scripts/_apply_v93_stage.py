import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="algorcode_stage",
    sslmode="disable",
    connect_timeout=10,
)

conn = psycopg2.connect(**STAGE)
conn.autocommit = False
cur = conn.cursor()

cur.execute(
    "SELECT column_name FROM information_schema.columns "
    "WHERE table_name='tbl_fulfillment' AND table_schema='public'"
)
existing = {r[0] for r in cur.fetchall()}
print("tbl_fulfillment kolonlari:", sorted(existing))

cur.execute(
    "SELECT column_name FROM information_schema.columns "
    "WHERE table_name='tbl_plan_package' AND table_schema='public' "
    "AND column_name IN ('trial_days','trial_eligible')"
)
trial_cols = [r[0] for r in cur.fetchall()]
print("tbl_plan_package trial kolonlari:", trial_cols)

cur.execute(
    "SELECT EXISTS(SELECT 1 FROM information_schema.tables "
    "WHERE table_name='tbl_trial_log' AND table_schema='public')"
)
trial_log_exists = cur.fetchone()[0]
print("tbl_trial_log var mi:", trial_log_exists)

cur.execute("SELECT COUNT(*) FROM tbl_purchase WHERE purchase_type='TRIAL'")
trial_count = cur.fetchone()[0]
print("TRIAL purchase sayisi:", trial_count)

conn.close()
print("DONE")
