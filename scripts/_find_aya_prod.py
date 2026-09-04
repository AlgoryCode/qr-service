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

for term in ["aya", "roof", "lounge", "ayaroof"]:
    cur.execute(
        "SELECT id, email, first_name, last_name FROM tbl_user WHERE email ILIKE %s LIMIT 5",
        (f"%{term}%",),
    )
    rows = cur.fetchall()
    if rows:
        print(f"users like {term}:", rows)

cur.execute(
    """
    SELECT menu_id, qr_id, business_name, email, user_id, theme_id
    FROM tbl_menu
    WHERE is_deleted = false
      AND (
        business_name ILIKE '%aya%'
        OR business_name ILIKE '%roof%'
        OR email ILIKE '%aya%'
        OR email ILIKE '%roof%'
      )
    ORDER BY menu_id
    """
)
menus = cur.fetchall()
print("menus:", menus)

if menus:
    menu_id = menus[0][0]
    cur.execute(
        """
        SELECT id, name, slug, sort_order, image_url
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (menu_id,),
    )
    print(f"\nmenu {menu_id} categories:")
    for row in cur.fetchall():
        print(" ", row)

    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_menu_products
        WHERE menu_id = %s AND is_deleted = false
        """,
        (menu_id,),
    )
    print("product count:", cur.fetchone()[0])

cur.close()
conn.close()
