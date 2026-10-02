import psycopg2
from pathlib import Path

sql = Path(
    r"c:\Users\Tarik\Desktop\Services\qr-service\src\main\resources\db\migration\V97__batch_reports.sql"
).read_text(encoding="utf-8")

conn = psycopg2.connect(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=10,
)
conn.autocommit = True
cur = conn.cursor()
cur.execute(sql)
print("V97 applied")
cur.execute(
    "SELECT to_regclass('public.tbl_batch_reports'), to_regclass('public.tbl_batch_report_items')"
)
print(cur.fetchone())
conn.close()
