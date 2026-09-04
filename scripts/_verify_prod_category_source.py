import json
import urllib.request

import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

with urllib.request.urlopen("https://prod.qrapi.algorycode.com/menu/public/id/10", timeout=20) as resp:
    menu_payload = json.loads(resp.read().decode())

menu_id = menu_payload["menu"]["menuId"]
categories = menu_payload.get("categories", [])
print("menu_id", menu_id, "initial categories", len(categories))
print("sample api categories", [(c["id"], c["slug"]) for c in categories[:5]])

with urllib.request.urlopen(
    f"https://prod.qrapi.algorycode.com/menu/public/{menu_id}/categories?page=0&size=50",
    timeout=20,
) as resp:
    page = json.loads(resp.read().decode())
print("all categories via paging", len(page["content"]))

conn = psycopg2.connect(**PROD)
cur = conn.cursor()
cur.execute(
    "SELECT id, slug, name FROM tbl_main_category WHERE is_deleted = false ORDER BY id LIMIT 8"
)
print("tbl_main_category", cur.fetchall())
cur.execute(
    """
    SELECT EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema='public' AND table_name='tbl_menu_category'
    )
    """
)
print("tbl_menu_category exists", cur.fetchone()[0])
cur.close()
conn.close()
