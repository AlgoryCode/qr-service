import psycopg2

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
    SELECT COUNT(DISTINCT p.menu_id)
    FROM tbl_menu_products p
    WHERE p.is_deleted = false
    """
)
print("menus with products:", cur.fetchone()[0])

cur.execute(
    """
    SELECT COUNT(*)
    FROM tbl_menu_products p
    LEFT JOIN tbl_sub_category s ON s.id = p.sub_category_id
    WHERE p.is_deleted = false AND s.id IS NULL
    """
)
print("products with missing global sub_category:", cur.fetchone()[0])

cur.execute(
    """
    SELECT COUNT(*)
    FROM tbl_menu_products p
    WHERE p.is_deleted = false
      AND p.descriptor_category_id IS NOT NULL
    """
)
print("products with descriptor_category_id set:", cur.fetchone()[0])

cur.execute(
    """
    SELECT COUNT(*)
    FROM tbl_menu_product_pairing pp
    WHERE pp.target_sub_category_id IS NOT NULL
       OR pp.target_main_category_id IS NOT NULL
    """
)
print("pairings with target category refs:", cur.fetchone()[0])

cur.close()
conn.close()
