from __future__ import annotations

import re
import unicodedata
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

NEW_MAINS = [
    ("kirmizi_saraplar", "Kırmızı Şaraplar", 17),
    ("sparkling_wine", "Sparkling Wine", 18),
    ("saraplar", "Şaraplar", 19),
    ("rakilar", "Rakılar", 20),
]


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", (text or "").lower())
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.replace("ı", "i").replace("İ", "i")
    return re.sub(r"\s+", " ", text).strip()


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
        SELECT id, image_url, image_key FROM tbl_menu_category
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


def upsert_sub(cur, menu_id: int, main_id: int, slug: str, name: str, sort_order: int = 0) -> int:
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
            SET menu_category_id = %s, name = %s, sort_order = %s, updated_at = %s, is_deleted = false
            WHERE id = %s
            """,
            (main_id, name, sort_order, ts, row[0]),
        )
        return row[0]
    cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_sub_category")
    new_id = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO tbl_menu_sub_category (
            id, menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, false)
        """,
        (new_id, menu_id, main_id, slug, name, sort_order, ts, ts),
    )
    return new_id


def copy_cover_if_empty(cur, target_main_id: int, source_sub_slug: str, menu_id: int) -> None:
    cur.execute(
        """
        SELECT image_url, image_key FROM tbl_menu_category
        WHERE id = %s
        """,
        (target_main_id,),
    )
    target = cur.fetchone()
    if target and target[0]:
        return
    cur.execute(
        """
        SELECT p.image_url
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        WHERE sc.menu_id = %s AND sc.slug = %s AND p.is_deleted = false
          AND p.image_url IS NOT NULL AND p.image_url <> ''
        ORDER BY p.sort_order, p.product_id
        LIMIT 1
        """,
        (menu_id, source_sub_slug),
    )
    row = cur.fetchone()
    if not row:
        return
    cur.execute(
        """
        UPDATE tbl_menu_category
        SET image_url = %s, updated_at = %s
        WHERE id = %s
        """,
        (row[0], now(), target_main_id),
    )


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
    main_ids: dict[str, int] = {}
    for slug, name, sort_order in NEW_MAINS:
        main_ids[slug] = upsert_main(cur, menu_id, user_id, slug, name, sort_order)

    kirmizi_sub = upsert_sub(
        cur, menu_id, main_ids["kirmizi_saraplar"], "kirmizi_saraplar", "Kırmızı Şaraplar", 0
    )
    sparkling_sub = upsert_sub(
        cur, menu_id, main_ids["sparkling_wine"], "sparkling_wine", "Sparkling Wine", 0
    )

    cur.execute(
        """
        SELECT id FROM tbl_menu_sub_category
        WHERE menu_id = %s AND slug = 'sampanya_prosecco'
        ORDER BY is_deleted ASC, id ASC
        LIMIT 1
        """,
        (menu_id,),
    )
    old_sparkling = cur.fetchone()
    if old_sparkling and old_sparkling[0] != sparkling_sub:
        cur.execute(
            """
            UPDATE tbl_menu_products
            SET sub_category_id = %s, updated_at = %s
            WHERE sub_category_id = %s AND is_deleted = false
            """,
            (sparkling_sub, ts, old_sparkling[0]),
        )
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET is_deleted = true, updated_at = %s
            WHERE id = %s
            """,
            (ts, old_sparkling[0]),
        )
        print(f"[{label}] moved sampanya_prosecco products -> sparkling_wine")

    for keep_slug, keep_name, sort_order in (
        ("beyaz_saraplar", "Beyaz Şaraplar", 0),
        ("roze_saraplar", "Roze Şaraplar", 1),
        ("yari_tatli_saraplar", "Yarı Tatlı Şaraplar", 2),
    ):
        upsert_sub(cur, menu_id, main_ids["saraplar"], keep_slug, keep_name, sort_order)

    cur.execute(
        """
        SELECT product_id, name FROM tbl_menu_products
        WHERE menu_id = %s AND is_deleted = false
        """,
        (menu_id,),
    )
    for product_id, name in cur.fetchall():
        n = normalize(name)
        if n == "thia shiraz semi sweet":
            cur.execute(
                """
                UPDATE tbl_menu_products
                SET sub_category_id = %s, updated_at = %s
                WHERE product_id = %s
                """,
                (kirmizi_sub, ts, product_id),
            )
            print(f"[{label}] {name} -> kirmizi_saraplar")
        elif "prosecco" in n or "sparkling" in n or "sampanya" in n or "champagne" in n:
            if "glass" in n:
                continue
            cur.execute(
                """
                UPDATE tbl_menu_products
                SET sub_category_id = %s, updated_at = %s
                WHERE product_id = %s
                """,
                (sparkling_sub, ts, product_id),
            )
            print(f"[{label}] {name} -> sparkling_wine")

    cur.execute(
        """
        SELECT sc.id, sc.slug,
               COUNT(p.product_id) FILTER (WHERE p.is_deleted = false)
        FROM tbl_menu_sub_category sc
        LEFT JOIN tbl_menu_products p ON p.sub_category_id = sc.id
        WHERE sc.menu_id = %s AND sc.is_deleted = false
          AND sc.slug = 'yari_tatli_saraplar'
        GROUP BY sc.id, sc.slug
        """,
        (menu_id,),
    )
    for sub_id, slug, count in cur.fetchall():
        if count == 0:
            cur.execute(
                """
                UPDATE tbl_menu_sub_category
                SET is_deleted = true, updated_at = %s
                WHERE id = %s
                """,
                (ts, sub_id),
            )
            print(f"[{label}] soft-delete empty {slug}")

    copy_cover_if_empty(cur, main_ids["kirmizi_saraplar"], "kirmizi_saraplar", menu_id)
    copy_cover_if_empty(cur, main_ids["sparkling_wine"], "sparkling_wine", menu_id)

    conn.commit()

    cur.execute(
        """
        SELECT mc.sort_order, mc.slug, mc.name, sc.slug, sc.name,
               COUNT(p.product_id) FILTER (WHERE p.is_deleted = false)
        FROM tbl_menu_category mc
        JOIN tbl_menu_sub_category sc ON sc.menu_category_id = mc.id AND sc.is_deleted = false
        LEFT JOIN tbl_menu_products p ON p.sub_category_id = sc.id
        WHERE mc.menu_id = %s AND mc.is_deleted = false
          AND mc.slug IN ('kirmizi_saraplar', 'sparkling_wine', 'saraplar', 'rakilar')
        GROUP BY mc.sort_order, mc.slug, mc.name, sc.sort_order, sc.slug, sc.name
        ORDER BY mc.sort_order, sc.sort_order
        """,
        (menu_id,),
    )
    print(f"[{label}] wine tree:")
    for row in cur.fetchall():
        print(" ", row)

    for slug in ("kirmizi_saraplar", "sparkling_wine"):
        cur.execute(
            """
            SELECT p.name, p.price
            FROM tbl_menu_products p
            JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
            JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
            WHERE p.menu_id = %s AND p.is_deleted = false AND mc.slug = %s
            ORDER BY p.name
            """,
            (menu_id, slug),
        )
        print(f"[{label}] {slug}:")
        for row in cur.fetchall():
            print(" ", row)

    cur.close()
    conn.close()


if __name__ == "__main__":
    setup("PROD", PROD)
    setup("STAGE", STAGE)
