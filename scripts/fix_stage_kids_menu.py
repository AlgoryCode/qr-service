from datetime import datetime, timezone

import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)

PRODUCTS = [
    ("Elsa Anna - Sausage & French Fries", "Sausage & French Fries — 350 gr.", 0),
    ("Princess Cinderella - Spaghetti Bolognese", "Spaghetti Bolognese — 400 gr.", 1),
    ("Spiderman - Hamburger & French Fries", "Hamburger & French Fries — 150 gr.", 2),
    ("Batman - Cheeseburger & French Fries", "Cheeseburger & French Fries — 150 gr.", 3),
    ("Mickey Mouse - Chicken Nuggets & French Fries", "Chicken Nuggets & French Fries — 350 gr.", 4),
]


def main() -> None:
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    conn = psycopg2.connect(**STAGE)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT m.menu_id, m.user_id FROM tbl_menu m
        JOIN tbl_user u ON u.id = m.user_id
        WHERE u.email = %s AND m.is_deleted = false
        ORDER BY m.menu_id LIMIT 1
        """,
        ("ulasbayram61@gmail.com",),
    )
    menu_id, user_id = cur.fetchone()

    cur.execute(
        """
        SELECT id FROM tbl_menu_category
        WHERE menu_id = %s AND slug = %s
        ORDER BY is_deleted ASC, id ASC LIMIT 1
        """,
        (menu_id, "cocuk_menusu"),
    )
    row = cur.fetchone()
    if row:
        cat_id = row[0]
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET is_deleted = false, name = %s, sort_order = 10, updated_at = %s, user_id = %s
            WHERE id = %s
            """,
            ("Çocuk Menüsü", now, user_id, cat_id),
        )
    else:
        cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_category")
        cat_id = cur.fetchone()[0]
        cur.execute(
            """
            INSERT INTO tbl_menu_category (
                id, menu_id, user_id, slug, name, sort_order, created_at, updated_at, is_deleted
            ) VALUES (%s, %s, %s, %s, %s, 10, %s, %s, false)
            """,
            (cat_id, menu_id, user_id, "cocuk_menusu", "Çocuk Menüsü", now, now),
        )

    cur.execute(
        """
        SELECT id FROM tbl_menu_sub_category
        WHERE menu_id = %s AND slug = %s
        ORDER BY is_deleted ASC, id ASC LIMIT 1
        """,
        (menu_id, "cocuk_ana_yemekleri"),
    )
    row = cur.fetchone()
    if row:
        sub_id = row[0]
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET is_deleted = false, menu_category_id = %s, name = %s, updated_at = %s
            WHERE id = %s
            """,
            (cat_id, "Çocuk Ana Yemekleri", now, sub_id),
        )
    else:
        cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_sub_category")
        sub_id = cur.fetchone()[0]
        cur.execute(
            """
            INSERT INTO tbl_menu_sub_category (
                id, menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted
            ) VALUES (%s, %s, %s, %s, %s, 0, %s, %s, false)
            """,
            (sub_id, menu_id, cat_id, "cocuk_ana_yemekleri", "Çocuk Ana Yemekleri", now, now),
        )

    first_image = None
    for name, desc, sort_order in PRODUCTS:
        cur.execute(
            """
            SELECT product_id, image_url FROM tbl_menu_products
            WHERE menu_id = %s AND is_deleted = false AND name = %s
            """,
            (menu_id, name),
        )
        found = cur.fetchone()
        if not found:
            print("MISS", name)
            continue
        cur.execute(
            """
            UPDATE tbl_menu_products
            SET sub_category_id = %s, price = 670, description = %s, sort_order = %s,
                updated_at = %s, available = true
            WHERE product_id = %s
            """,
            (sub_id, desc, sort_order, now, found[0]),
        )
        print("MOVED", name)
        if found[1] and not first_image:
            first_image = found[1]

    if first_image:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET image_url = COALESCE(NULLIF(image_url, ''), %s), updated_at = %s
            WHERE id = %s
            """,
            (first_image, now, cat_id),
        )

    conn.commit()
    cur.execute(
        """
        SELECT p.sort_order, p.name, mc.name
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND mc.slug = %s AND p.is_deleted = false
        ORDER BY p.sort_order
        """,
        (menu_id, "cocuk_menusu"),
    )
    print("final:")
    for row in cur.fetchall():
        print(row)
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
