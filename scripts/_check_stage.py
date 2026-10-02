import psycopg2

conn = psycopg2.connect(
    host="185.184.210.52", port=5433,
    dbname="algoryqrdb-stage", user="postgres",
    password="postgres_stage", sslmode="disable", connect_timeout=8
)
cur = conn.cursor()

cur.execute("SELECT column_name FROM information_schema.columns WHERE table_name='tbl_fulfillment' AND table_schema='public'")
print("tbl_fulfillment:", sorted([r[0] for r in cur.fetchall()]))

cur.execute("SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name='tbl_trial_log' AND table_schema='public')")
print("tbl_trial_log var mi:", cur.fetchone()[0])

cur.execute("SELECT column_name FROM information_schema.columns WHERE table_name='tbl_plan_package' AND table_schema='public' AND column_name IN ('trial_days','trial_eligible')")
print("plan_package trial cols:", [r[0] for r in cur.fetchall()])

conn.close()
print("BAGLANTI BASARILI")
