import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)
PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)


def inspect(label: str, cfg: dict) -> None:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    print(f"=== {label} ===")
    cur.execute(
        """
        SELECT menu_id, qr_id, business_name, email, user_id, theme_id
        FROM tbl_menu
        WHERE is_deleted = false
          AND (
            business_name ILIKE '%paradise%'
            OR business_name ILIKE '%aya%roof%'
            OR email ILIKE '%ulasbayram%'
            OR email ILIKE '%ayaroof%'
          )
        ORDER BY menu_id
        """
    )
    menus = cur.fetchall()
    print("menus", menus)
    for menu_id, qr_id, bname, email, user_id, theme in menus:
        cur.execute(
            """
            SELECT id, slug, name, sort_order
            FROM tbl_menu_category
            WHERE menu_id = %s AND is_deleted = false
            ORDER BY sort_order, id
            """,
            (menu_id,),
        )
        cats = cur.fetchall()
        print(f"menu {menu_id} qr={qr_id} {bname} theme={theme} user={user_id} ({len(cats)} cats):")
        for row in cats:
            print(" ", row)
        cur.execute(
            """
            SELECT id, menu_category_id, slug, name, sort_order
            FROM tbl_menu_sub_category
            WHERE menu_id = %s AND is_deleted = false
            ORDER BY menu_category_id, sort_order, id
            """,
            (menu_id,),
        )
        subs = cur.fetchall()
        print(f"  subcategories: {len(subs)}")
        for row in subs[:20]:
            print("   ", row)
        if len(subs) > 20:
            print("   ...")
    cur.close()
    conn.close()


if __name__ == "__main__":
    inspect("STAGE", STAGE)
    inspect("PROD", PROD)
