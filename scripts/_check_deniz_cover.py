import psycopg2
import json

conn = psycopg2.connect(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)
cur = conn.cursor()

cur.execute(
    """
    SELECT m.menu_id, m.business_name, m.email, m.user_id, u.email
    FROM tbl_menu m
    JOIN tbl_user u ON u.id = m.user_id
    WHERE u.email = 'trkhamarat@gmail.com' AND m.is_deleted = false
    ORDER BY m.menu_id
    """
)
print("menus:", cur.fetchall())

cur.execute(
    """
    SELECT c.id, c.menu_id, c.name, c.slug, c.image_url, c.image_key, c.sort_order, c.is_deleted
    FROM tbl_menu_category c
    JOIN tbl_menu m ON m.menu_id = c.menu_id
    JOIN tbl_user u ON u.id = m.user_id
    WHERE u.email = 'trkhamarat@gmail.com'
      AND (c.slug ILIKE '%deniz%' OR c.name ILIKE '%deniz%')
    ORDER BY c.menu_id, c.id
    """
)
rows = cur.fetchall()
print("deniz categories:", rows)

cur.execute(
    """
    SELECT c.id, c.menu_id, c.name, c.slug, c.image_url, c.image_key
    FROM tbl_menu_category c
    JOIN tbl_menu m ON m.menu_id = c.menu_id
    JOIN tbl_user u ON u.id = m.user_id
    WHERE u.email = 'trkhamarat@gmail.com' AND c.is_deleted = false
    ORDER BY c.menu_id, c.sort_order, c.id
    """
)
print("all categories with covers:")
for r in cur.fetchall():
    has_cover = bool(r[4] or r[5])
    print(r[0], r[2], r[3], "cover=" + ("yes" if has_cover else "no"), r[4])

cur.close()
conn.close()
