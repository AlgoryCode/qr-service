from __future__ import annotations

import re
import unicodedata

import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

AYA_MENU = 16
PARADISE_MENU = 17


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", (text or "").lower())
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.replace("ı", "i").replace("İ", "i")
    return re.sub(r"[^a-z0-9]+", "", text)


def load_drinks(cur, menu_id: int):
    cur.execute(
        """
        SELECT p.product_id, p.name, p.image_url, mc.slug, mc.name
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s
          AND p.is_deleted = false
          AND (
            mc.slug LIKE 'drink-%%'
            OR mc.slug IN ('icecekler', 'kadeh_icecekler')
            OR mc.slug ILIKE '%%sarap%%'
            OR mc.slug ILIKE '%%wine%%'
            OR mc.slug ILIKE '%%raki%%'
            OR mc.slug ILIKE '%%viski%%'
            OR mc.slug ILIKE '%%vodka%%'
            OR mc.slug ILIKE '%%kokteyl%%'
            OR mc.slug ILIKE '%%bira%%'
            OR mc.slug ILIKE '%%sampanya%%'
            OR mc.slug ILIKE '%%prosecco%%'
            OR mc.name ILIKE '%%şarap%%'
            OR mc.name ILIKE '%%sarap%%'
            OR mc.name ILIKE '%%wine%%'
            OR mc.name ILIKE '%%rakı%%'
            OR mc.name ILIKE '%%viski%%'
            OR mc.name ILIKE '%%vodka%%'
            OR mc.name ILIKE '%%kokteyl%%'
            OR mc.name ILIKE '%%bira%%'
            OR mc.name ILIKE '%%şampanya%%'
            OR mc.name ILIKE '%%prosecco%%'
            OR mc.name ILIKE '%%gin%%'
            OR mc.name ILIKE '%%tekila%%'
            OR mc.name ILIKE '%%konyak%%'
            OR mc.name ILIKE '%%likör%%'
            OR mc.name ILIKE '%%içecek%%'
          )
        ORDER BY mc.sort_order, p.sort_order, p.product_id
        """,
        (menu_id,),
    )
    return cur.fetchall()


def main() -> None:
    conn = psycopg2.connect(**PROD)
    cur = conn.cursor()

    cur.execute(
        """
        SELECT mc.id, mc.slug, mc.name
        FROM tbl_menu_category mc
        WHERE mc.menu_id = %s AND mc.is_deleted = false
        ORDER BY mc.sort_order
        """,
        (PARADISE_MENU,),
    )
    print("PARADISE CATEGORIES:")
    for row in cur.fetchall():
        print(row)

    paradise = load_drinks(cur, PARADISE_MENU)
    aya = load_drinks(cur, AYA_MENU)

    print(f"\nParadise drinks: {len(paradise)}")
    print(f"AYA drinks: {len(aya)}")

    paradise_by_norm: dict[str, list] = {}
    for row in paradise:
        key = normalize(row[1])
        paradise_by_norm.setdefault(key, []).append(row)

    matches = []
    for aya_row in aya:
        key = normalize(aya_row[1])
        candidates = paradise_by_norm.get(key, [])
        with_img = [c for c in candidates if c[2]]
        if with_img:
            matches.append((aya_row, with_img[0]))
        elif candidates:
            matches.append((aya_row, candidates[0]))

    print(f"\nExact name matches: {len(matches)}")
    for aya_row, par_row in matches:
        print(
            f"AYA {aya_row[0]} {aya_row[1]!r} img={bool(aya_row[2])} | "
            f"PAR {par_row[0]} {par_row[1]!r} img={bool(par_row[2])} { (par_row[2] or '')[:60]}"
        )

    aya_only = [r for r in aya if normalize(r[1]) not in paradise_by_norm]
    print(f"\nAYA drinks with no paradise exact match: {len(aya_only)}")
    for row in aya_only[:40]:
        print(" ", row[0], row[1], row[3])

    # also show paradise drinks with images for fuzzy ideas
    print("\nParadise drinks WITH image sample:")
    for row in paradise:
        if row[2]:
            print(" ", row[0], row[1], row[3], row[2][:60])

    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
