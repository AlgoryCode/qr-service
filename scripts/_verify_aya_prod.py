import json
import urllib.request

import psycopg2

conn = psycopg2.connect(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)
cur = conn.cursor()

cur.execute(
    """
    SELECT mc.sort_order, mc.name, mc.slug, mc.image_url
    FROM tbl_menu_category mc
    WHERE mc.menu_id = 16 AND mc.is_deleted = false
    ORDER BY mc.sort_order
    """
)
print("=== MAIN CATEGORIES ===")
for sort_order, name, slug, image_url in cur.fetchall():
    status = "no-url"
    if image_url:
        try:
            resp = urllib.request.urlopen(image_url, timeout=10)
            status = f"{resp.status} ({len(resp.read()) // 1024}KB)"
        except Exception as exc:
            status = f"FAIL {type(exc).__name__}"
    print(f"{sort_order:2} {name:20} {slug:18} CDN:{status}")

cur.execute(
    """
    SELECT mc.name, sc.name, sc.slug, sc.sort_order, COUNT(*)
    FROM tbl_menu_products mp
    JOIN tbl_menu_sub_category sc ON sc.id = mp.sub_category_id
    JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
    WHERE mp.menu_id = 16 AND mp.is_deleted = false
    GROUP BY mc.name, mc.slug, mc.sort_order, sc.name, sc.slug, sc.sort_order
    ORDER BY mc.sort_order, sc.sort_order
    """
)
print("\n=== PRODUCTS BY SUB ===")
for main_name, sub_name, sub_slug, sub_sort, count in cur.fetchall():
    print(f"{main_name:15} > {sub_name:22} ({count:3}) slug={sub_slug}")

cur.close()
conn.close()

with urllib.request.urlopen("https://prod.qrapi.algorycode.com/menu/public/id/35", timeout=20) as resp:
    data = json.loads(resp.read())

cats = data.get("categories") or []
print(f"\n=== PUBLIC API ({len(cats)} cats) ===")
for cat in cats:
    img = "YES" if cat.get("imageUrl") else "NO"
    print(f"{cat.get('sortOrder'):2} {cat.get('name'):20} img={img}")
