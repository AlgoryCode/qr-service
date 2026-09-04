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

GLASS_EXACT = {
    "red wine (glass)",
    "white wine (glass)",
    "rose wine (glass)",
    "prosecco sparkling wine glass",
    "wine",
}


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", text.lower())
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

    cur.execute(
        """
        SELECT id, slug, sort_order, image_url, image_key
        FROM tbl_menu_category
        WHERE menu_id = %s AND slug IN ('shot_icecekler', 'kadeh_icecekler')
        ORDER BY is_deleted ASC, id ASC
        """,
        (menu_id,),
    )
    existing = {slug: row for row in cur.fetchall() for slug in [row[1]]}
    shot = existing.get("shot_icecekler")
    kadeh = existing.get("kadeh_icecekler")

    if shot and not kadeh:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET slug = 'kadeh_icecekler', name = 'Kadeh İçecekler', updated_at = %s, is_deleted = false
            WHERE id = %s
            """,
            (ts, shot[0]),
        )
        kadeh_main_id = shot[0]
        sort_order = shot[2]
        print(f"[{label}] renamed shot_icecekler -> kadeh_icecekler ({kadeh_main_id})")
    elif kadeh:
        kadeh_main_id = kadeh[0]
        sort_order = kadeh[2]
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET name = 'Kadeh İçecekler', sort_order = %s, updated_at = %s, is_deleted = false
            WHERE id = %s
            """,
            (sort_order, ts, kadeh_main_id),
        )
        if shot and shot[0] != kadeh_main_id:
            cur.execute(
                """
                UPDATE tbl_menu_category
                SET is_deleted = true, updated_at = %s
                WHERE id = %s
                """,
                (ts, shot[0]),
            )
    else:
        sort_order = 14
        kadeh_main_id = upsert_main(cur, menu_id, user_id, "kadeh_icecekler", "Kadeh İçecekler", sort_order)
        print(f"[{label}] created kadeh_icecekler ({kadeh_main_id})")

    kadeh_sub_id = upsert_sub(cur, menu_id, kadeh_main_id, "kadeh_icecekler", "Kadeh İçecekler")

    cur.execute(
        """
        SELECT id FROM tbl_menu_sub_category
        WHERE menu_id = %s AND slug = 'shot_icecekler' AND is_deleted = false
        """,
        (menu_id,),
    )
    shot_sub = cur.fetchone()
    if shot_sub and shot_sub[0] != kadeh_sub_id:
        cur.execute(
            """
            UPDATE tbl_menu_products
            SET sub_category_id = %s, updated_at = %s
            WHERE sub_category_id = %s AND is_deleted = false
            """,
            (kadeh_sub_id, ts, shot_sub[0]),
        )
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET is_deleted = true, updated_at = %s
            WHERE id = %s
            """,
            (ts, shot_sub[0]),
        )
        print(f"[{label}] moved products from shot sub -> kadeh sub")

    cur.execute(
        """
        SELECT p.product_id, p.name, p.sub_category_id
        FROM tbl_menu_products p
        WHERE p.menu_id = %s AND p.is_deleted = false
        """,
        (menu_id,),
    )
    moved = 0
    for product_id, name, _sub in cur.fetchall():
        if normalize(name) not in GLASS_EXACT:
            continue
        cur.execute(
            """
            UPDATE tbl_menu_products
            SET sub_category_id = %s, updated_at = %s
            WHERE product_id = %s
            """,
            (kadeh_sub_id, ts, product_id),
        )
        moved += 1
        print(f"[{label}] {name} -> kadeh_icecekler")

    conn.commit()

    cur.execute(
        """
        SELECT mc.slug, mc.name, sc.slug, COUNT(p.product_id) FILTER (WHERE p.is_deleted = false)
        FROM tbl_menu_category mc
        JOIN tbl_menu_sub_category sc ON sc.menu_category_id = mc.id AND sc.is_deleted = false
        LEFT JOIN tbl_menu_products p ON p.sub_category_id = sc.id
        WHERE mc.menu_id = %s AND mc.is_deleted = false
          AND mc.slug IN ('kadeh_icecekler', 'shot_icecekler', 'saraplar')
        GROUP BY mc.sort_order, mc.slug, mc.name, sc.sort_order, sc.slug
        ORDER BY mc.sort_order, sc.sort_order
        """,
        (menu_id,),
    )
    print(f"[{label}] glass moves={moved} tree:")
    for row in cur.fetchall():
        print(" ", row)
    cur.execute(
        """
        SELECT p.name, p.price
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND p.is_deleted = false AND mc.slug = 'kadeh_icecekler'
        ORDER BY p.name
        """,
        (menu_id,),
    )
    print(f"[{label}] kadeh products:")
    for row in cur.fetchall():
        print(" ", row)

    cur.close()
    conn.close()


if __name__ == "__main__":
    setup("PROD", PROD)
    setup("STAGE", STAGE)
