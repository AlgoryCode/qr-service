import psycopg2
import urllib.request
import json

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

conn = psycopg2.connect(**PROD)
cur = conn.cursor()
cur.execute(
    """
    SELECT table_name FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name IN ('tbl_menu_category', 'tbl_menu_sub_category')
    """
)
print("menu category tables on prod:", cur.fetchall())

cur.execute("SELECT menu_id, qr_id, business_name FROM tbl_menu WHERE qr_id = 10")
print("menu qr 10:", cur.fetchone())

cur.execute(
    """
    SELECT column_name FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'tbl_menu_category'
    ORDER BY ordinal_position
    """
)
print("tbl_menu_category columns:", [r[0] for r in cur.fetchall()])

cur.close()
conn.close()

with urllib.request.urlopen("https://prod.qrapi.algorycode.com/menu/public/id/10", timeout=20) as resp:
    data = json.loads(resp.read().decode())
print("prod public categories page0:", len(data.get("categories", [])), "total:", data.get("categoryTotalElements"))
