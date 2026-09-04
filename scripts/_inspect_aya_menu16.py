import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

MENU_ID = 16

conn = psycopg2.connect(**PROD)
cur = conn.cursor()

cur.execute(
    """
    SELECT c.id, c.name, c.slug, c.sort_order,
           COUNT(sc.id) AS sub_count
    FROM tbl_menu_category c
    LEFT JOIN tbl_menu_sub_category sc
      ON sc.menu_category_id = c.id AND sc.is_deleted = false
    WHERE c.menu_id = %s AND c.is_deleted = false
    GROUP BY c.id, c.name, c.slug, c.sort_order
    ORDER BY c.sort_order, c.id
    """,
    (MENU_ID,),
)
print("=== MAIN CATEGORIES ===")
for row in cur.fetchall():
    print(row)

cur.execute(
    """
    SELECT sc.id, sc.menu_category_id, sc.name, sc.slug, sc.sort_order
    FROM tbl_menu_sub_category sc
    WHERE sc.menu_id = %s AND sc.is_deleted = false
    ORDER BY sc.menu_category_id, sc.sort_order, sc.id
    """,
    (MENU_ID,),
)
print("\n=== SUB CATEGORIES ===")
for row in cur.fetchall():
    print(row)

cur.execute(
    """
    SELECT sc.id, sc.name, COUNT(p.product_id)
    FROM tbl_menu_sub_category sc
    LEFT JOIN tbl_menu_products p
      ON p.sub_category_id = sc.id AND p.is_deleted = false
    WHERE sc.menu_id = %s AND sc.is_deleted = false
    GROUP BY sc.id, sc.name
    ORDER BY COUNT(p.product_id) DESC
    LIMIT 30
    """,
    (MENU_ID,),
)
print("\n=== TOP SUBS BY PRODUCT COUNT ===")
for row in cur.fetchall():
    print(row)

cur.execute(
    """
    SELECT p.product_id, p.name, p.price, sc.name, mc.name
    FROM tbl_menu_products p
    JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
    JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
    WHERE p.menu_id = %s AND p.is_deleted = false
    ORDER BY mc.sort_order, sc.sort_order, p.sort_order
    LIMIT 40
    """,
    (MENU_ID,),
)
print("\n=== SAMPLE PRODUCTS ===")
for row in cur.fetchall():
    print(row)

cur.close()
conn.close()
