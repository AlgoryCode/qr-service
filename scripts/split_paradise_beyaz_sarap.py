from __future__ import annotations

from datetime import datetime, timezone

import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)
STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)

TARGET_EMAIL = "ulasbayram61@gmail.com"


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def resolve_menu(cur) -> tuple[int, int] | None:
    cur.execute(
        """
        SELECT m.menu_id, m.user_id
        FROM tbl_menu m
        JOIN tbl_user u ON u.id = m.user_id
        WHERE u.email = %s AND m.is_deleted = false
        ORDER BY m.menu_id
        LIMIT 1
        """,
        (TARGET_EMAIL,),
    )
    row = cur.fetchone()
    return (row[0], row[1]) if row else None


def upsert_main(cur, menu_id: int, user_id: int, slug: str, name: str, sort_order: int) -> int:
    cur.execute(
        """
        SELECT id FROM tbl_menu_category
        WHERE menu_id = %s AND slug = %s
        ORDER BY is_deleted ASC, id ASC
        LIMIT 1
        """,
        (menu_id, slug),
    )
    row = cur.fetchone()
    ts = now()
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET name = %s, sort_order = %s, updated_at = %s, is_deleted = false, user_id = %s
            WHERE id = %s
            """,
            (name, sort_order, ts, user_id, row[0]),
        )
        return row[0]
    cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_category")
    new_id = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO tbl_menu_category (
            id, menu_id, user_id, slug, name, sort_order, created_at, updated_at, is_deleted
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, false)
        """,
        (new_id, menu_id, user_id, slug, name, sort_order, ts, ts),
    )
    return new_id


def upsert_sub(cur, menu_id: int, main_id: int, slug: str, name: str) -> int:
    cur.execute(
        """
        SELECT id FROM tbl_menu_sub_category
        WHERE menu_id = %s AND slug = %s
        ORDER BY is_deleted ASC, id ASC
        LIMIT 1
        """,
        (menu_id, slug),
    )
    row = cur.fetchone()
    ts = now()
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET menu_category_id = %s, name = %s, sort_order = 0, updated_at = %s, is_deleted = false
            WHERE id = %s
            """,
            (main_id, name, ts, row[0]),
        )
        return row[0]
    cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_sub_category")
    new_id = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO tbl_menu_sub_category (
            id, menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted
        )
        VALUES (%s, %s, %s, %s, %s, 0, %s, %s, false)
        """,
        (new_id, menu_id, main_id, slug, name, ts, ts),
    )
    return new_id


def setup(label: str, cfg: dict) -> None:
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()
    resolved = resolve_menu(cur)
    if resolved is None:
        print(f"[{label}] no menu")
        cur.close()
        conn.close()
        return

    menu_id, user_id = resolved
    ts = now()

    # sort: kirmizi 17, beyaz 18, sparkling 19, saraplar(roze) 20, rakilar 21
    beyaz_main = upsert_main(cur, menu_id, user_id, "beyaz_saraplar", "Beyaz Şaraplar", 18)
    beyaz_sub = upsert_sub(cur, menu_id, beyaz_main, "beyaz_saraplar", "Beyaz Şaraplar")

    upsert_main(cur, menu_id, user_id, "kirmizi_saraplar", "Kırmızı Şaraplar", 17)
    upsert_main(cur, menu_id, user_id, "sparkling_wine", "Sparkling Wine", 19)
    saraplar_main = upsert_main(cur, menu_id, user_id, "saraplar", "Şaraplar", 20)
    upsert_main(cur, menu_id, user_id, "rakilar", "Rakılar", 21)

    upsert_sub(cur, menu_id, saraplar_main, "roze_saraplar", "Roze Şaraplar")

    cur.execute(
        """
        SELECT p.image_url
        FROM tbl_menu_products p
        WHERE p.sub_category_id = %s AND p.is_deleted = false
          AND p.image_url IS NOT NULL AND p.image_url <> ''
        ORDER BY p.sort_order, p.product_id
        LIMIT 1
        """,
        (beyaz_sub,),
    )
    cover = cur.fetchone()
    if cover:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET image_url = COALESCE(NULLIF(image_url, ''), %s), updated_at = %s
            WHERE id = %s
            """,
            (cover[0], ts, beyaz_main),
        )

    cur.execute(
        """
        SELECT sc.id, COUNT(p.product_id) FILTER (WHERE p.is_deleted = false)
        FROM tbl_menu_sub_category sc
        LEFT JOIN tbl_menu_products p ON p.sub_category_id = sc.id
        WHERE sc.menu_id = %s AND sc.is_deleted = false
          AND sc.menu_category_id = %s
          AND sc.slug NOT IN ('roze_saraplar')
        GROUP BY sc.id, sc.slug
        """,
        (menu_id, saraplar_main),
    )
    for sub_id, count in cur.fetchall():
        if count == 0:
            cur.execute(
                """
                UPDATE tbl_menu_sub_category
                SET is_deleted = true, updated_at = %s
                WHERE id = %s
                """,
                (ts, sub_id),
            )

    conn.commit()

    cur.execute(
        """
        SELECT mc.sort_order, mc.slug, mc.name, sc.slug,
               COUNT(p.product_id) FILTER (WHERE p.is_deleted = false)
        FROM tbl_menu_category mc
        JOIN tbl_menu_sub_category sc ON sc.menu_category_id = mc.id AND sc.is_deleted = false
        LEFT JOIN tbl_menu_products p ON p.sub_category_id = sc.id
        WHERE mc.menu_id = %s AND mc.is_deleted = false
          AND mc.slug IN ('kirmizi_saraplar','beyaz_saraplar','sparkling_wine','saraplar','rakilar')
        GROUP BY mc.sort_order, mc.slug, mc.name, sc.sort_order, sc.slug
        ORDER BY mc.sort_order, sc.sort_order
        """,
        (menu_id,),
    )
    print(f"[{label}] tree:")
    for row in cur.fetchall():
        print(" ", row)

    cur.execute(
        """
        SELECT p.name, p.price
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND p.is_deleted = false AND mc.slug = 'beyaz_saraplar'
        ORDER BY p.name
        """,
        (menu_id,),
    )
    print(f"[{label}] beyaz:")
    for row in cur.fetchall():
        print(" ", row)

    cur.close()
    conn.close()


if __name__ == "__main__":
    setup("PROD", PROD)
    setup("STAGE", STAGE)
