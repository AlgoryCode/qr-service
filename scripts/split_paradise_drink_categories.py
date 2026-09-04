from __future__ import annotations

import re
import unicodedata
from datetime import datetime

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

NEW_MAINS: list[tuple[str, str, int]] = [
    ("soguk_icecekler", "Soğuk İçecekler", 11),
    ("sicak_icecekler", "Sıcak İçecekler", 12),
    ("biralar", "Biralar", 13),
    ("kadeh_icecekler", "Kadeh İçecekler", 14),
    ("kokteyller", "Kokteyller", 15),
    ("yabanci_ickiler", "Yabancı İçkiler", 16),
    ("kirmizi_saraplar", "Kırmızı Şaraplar", 17),
    ("beyaz_saraplar", "Beyaz Şaraplar", 18),
    ("sparkling_wine", "Sparkling Wine", 19),
    ("saraplar", "Şaraplar", 20),
    ("rakilar", "Rakılar", 21),
]

WINE_SUBS: list[tuple[str, str, int]] = [
    ("roze_saraplar", "Roze Şaraplar", 0),
]

HOT_KEYS = (
    "cay",
    "tea",
    "kahve",
    "coffee",
    "nescafe",
    "espresso",
    "cappuccino",
    "americano",
    "latte",
)

KADEH_EXACT = {
    "brandy",
    "vodka",
    "raki",
    "rakı",
    "rum",
    "wine",
    "jagermeister",
    "jägermeister",
    "tequila",
    "baileys",
    "gin",
    "red wine (glass)",
    "white wine (glass)",
    "rose wine (glass)",
    "prosecco sparkling wine glass",
}

COCKTAIL_KEYS = (
    "cosmopolitan",
    "aperol",
    "mojito",
    "sex on the beach",
    "martini",
    "long island",
    "pina colada",
    "margarita",
)

BEER_KEYS = (
    "efes",
    "miller",
    "beck",
    "bomonti",
    "corona",
    "heineken",
    "clausthaler",
    "guinness",
)

COLD_KEYS = (
    "pepsi",
    "fanta",
    "sprite",
    "tonic",
    "ice tea",
    "redbull",
    "red bull",
    "lemonade",
    "limonata",
    "orange juice",
    "fruit juice",
    "ayran",
    "mineral water",
    "water ",
    " water",
)

FOREIGN_KEYS = (
    "belvedere",
    "istanbul blue",
    "istanblue",
    "absolut",
    "smirnoff",
    "gilbey",
    "johnnie walker",
    "chivas",
    "jack daniel",
)


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", text.lower())
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.replace("ı", "i").replace("İ", "i")
    return re.sub(r"\s+", " ", text).strip()


def classify_product(name: str) -> str:
    n = normalize(name)
    if n in KADEH_EXACT or n.replace("²", "i") in KADEH_EXACT:
        return "kadeh_icecekler"
    if any(k in n for k in COCKTAIL_KEYS):
        return "kokteyller"
    if any(k in n for k in BEER_KEYS):
        return "biralar"
    if any(k in n for k in FOREIGN_KEYS):
        return "yabanci_ickiler"
    if any(k in n for k in COLD_KEYS) or n.startswith("water"):
        return "soguk_icecekler"
    if any(k in n for k in HOT_KEYS):
        return "sicak_icecekler"
    if any(k in n for k in ("yeni raki", "tekirdag", "efe gold")):
        return "rakilar"
    if any(k in n for k in ("prosecco", "sampanya", "champagne", "sparkling")):
        if "glass" in n:
            return "kadeh_icecekler"
        return "sparkling_wine"
    if any(k in n for k in ("roze", "blush", "rose wine", "rose dry", "rose semi", "kup ", "pomegranate", "cherry liqueur")):
        return "roze_saraplar"
    if "semi sweet" in n and "thia shiraz" in n:
        return "kirmizi_saraplar"
    if any(
        k in n
        for k in (
            "cabernet",
            "merlot",
            "shiraz",
            "red wine",
            "epico merlot",
            "epico shiraz",
            "sarafin merlot",
            "sarafin shiraz",
            "besi bir yerde red",
        )
    ):
        return "kirmizi_saraplar"
    if any(
        k in n
        for k in (
            "white wine",
            "chardonnay",
            "sauvignon blanc",
            "besi bir yerde white",
            "epico chardonnay",
            "epico sauvignon",
            "sarafin chardonnay",
            "sarafin sauvignon",
            "thia chardonnay",
            "thia sauvignon",
        )
    ):
        return "beyaz_saraplar"
    if any(k in n for k in ("epico", "sarafin", "thia", "besi bir yerde")):
        return "kirmizi_saraplar"
    return "sicak_icecekler"


def resolve_wine_bucket(bucket: str) -> str:
    wine_slugs = {slug for slug, _, _ in WINE_SUBS}
    if bucket in wine_slugs:
        return "saraplar"
    return bucket


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
    now = datetime.utcnow()
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET name = %s, sort_order = %s, updated_at = %s, is_deleted = false, user_id = %s
            WHERE id = %s
            """,
            (name, sort_order, now, user_id, row[0]),
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
        (new_id, menu_id, user_id, slug, name, sort_order, now, now),
    )
    return new_id


def upsert_default_sub(
    cur, menu_id: int, main_id: int, slug: str, name: str, sort_order: int = 0
) -> int:
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
    now = datetime.utcnow()
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET menu_category_id = %s, name = %s, sort_order = %s, updated_at = %s, is_deleted = false
            WHERE id = %s
            """,
            (main_id, name, sort_order, now, row[0]),
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
        (new_id, menu_id, main_id, slug, name, sort_order, now, now),
    )
    return new_id


def drink_related_products(cur, menu_id: int) -> list[tuple[int, str, int | None, str, str]]:
    main_slugs = [slug for slug, _, _ in NEW_MAINS] + ["icecekler"]
    sub_slugs = (
        [slug for slug, _, _ in NEW_MAINS]
        + [slug for slug, _, _ in WINE_SUBS]
        + [
            "sicak_icecekler",
            "vodkalar",
            "viskiler",
            "likorler",
            "gin",
            "tekila",
            "konyak",
            "alkollu_icecekler",
        ]
    )
    cur.execute(
        """
        SELECT p.product_id, p.name, p.sub_category_id, mc.slug, sc.slug
        FROM tbl_menu_products p
        LEFT JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        LEFT JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND p.is_deleted = false
          AND (
            mc.slug = ANY(%s)
            OR sc.slug = ANY(%s)
          )
        ORDER BY p.product_id
        """,
        (menu_id, main_slugs, sub_slugs),
    )
    return cur.fetchall()


def soft_delete_empty_subs(cur, menu_id: int, keep_sub_ids: set[int], parent_main_ids: list[int]) -> None:
    now = datetime.utcnow()
    cur.execute(
        """
        SELECT sc.id, sc.slug,
               COUNT(p.product_id) FILTER (WHERE p.is_deleted = false) AS product_count
        FROM tbl_menu_sub_category sc
        LEFT JOIN tbl_menu_products p ON p.sub_category_id = sc.id
        WHERE sc.menu_id = %s
          AND sc.is_deleted = false
          AND sc.menu_category_id = ANY(%s)
        GROUP BY sc.id, sc.slug
        """,
        (menu_id, parent_main_ids),
    )
    for sub_id, slug, count in cur.fetchall():
        if sub_id in keep_sub_ids or count > 0:
            continue
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET is_deleted = true, updated_at = %s
            WHERE id = %s
            """,
            (now, sub_id),
        )
        print(f"  soft-delete empty sub {slug} ({sub_id})")


def setup(label: str, cfg: dict) -> None:
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()

    resolved = resolve_menu(cur)
    if resolved is None:
        print(f"[{label}] no menu for {TARGET_EMAIL}")
        cur.close()
        conn.close()
        return

    menu_id, user_id = resolved
    print(f"[{label}] menu={menu_id} user={user_id}")

    main_ids: dict[str, int] = {}
    sub_ids: dict[str, int] = {}
    for slug, name, sort_order in NEW_MAINS:
        main_ids[slug] = upsert_main(cur, menu_id, user_id, slug, name, sort_order)
        if slug == "saraplar":
            continue
        sub_ids[slug] = upsert_default_sub(cur, menu_id, main_ids[slug], slug, name, 0)

    for slug, name, sort_order in WINE_SUBS:
        sub_ids[slug] = upsert_default_sub(
            cur, menu_id, main_ids["saraplar"], slug, name, sort_order
        )

    now = datetime.utcnow()
    cur.execute(
        """
        UPDATE tbl_menu_category
        SET is_deleted = true, updated_at = %s
        WHERE menu_id = %s AND slug = 'icecekler' AND is_deleted = false
        """,
        (now, menu_id),
    )

    moved: dict[str, int] = {slug: 0 for slug, _, _ in NEW_MAINS}
    for product_id, name, _old_sub, _mc_slug, _sc_slug in drink_related_products(cur, menu_id):
        bucket = classify_product(name)
        target_sub = sub_ids[bucket]
        report_bucket = resolve_wine_bucket(bucket)
        cur.execute(
            """
            UPDATE tbl_menu_products
            SET sub_category_id = %s, updated_at = %s
            WHERE product_id = %s
            """,
            (target_sub, now, product_id),
        )
        moved[report_bucket] += 1
        print(f"  {name} -> {report_bucket}/{bucket}")

    keep_ids = set(sub_ids.values())
    soft_delete_empty_subs(cur, menu_id, keep_ids, list(main_ids.values()))

    cur.execute(
        """
        SELECT sc.id, sc.slug,
               COUNT(p.product_id) FILTER (WHERE p.is_deleted = false)
        FROM tbl_menu_sub_category sc
        LEFT JOIN tbl_menu_products p ON p.sub_category_id = sc.id
        WHERE sc.menu_id = %s
          AND sc.is_deleted = false
          AND sc.slug = ANY(%s)
        GROUP BY sc.id, sc.slug
        """,
        (
            menu_id,
            [
                "alkollu_kokteyller",
                "icecekler",
                "vodkalar",
                "viskiler",
                "likorler",
                "gin",
                "tekila",
                "konyak",
                "alkollu_icecekler",
                "caylar",
                "su",
                "sicak_icecekler",
            ],
        ),
    )
    for sub_id, slug, count in cur.fetchall():
        if count > 0 or sub_id in keep_ids:
            continue
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET is_deleted = true, updated_at = %s
            WHERE id = %s
            """,
            (now, sub_id),
        )
        print(f"  soft-delete obsolete sub {slug} ({sub_id})")

    conn.commit()
    print(f"[{label}] moved counts: {moved}")

    cur.execute(
        """
        SELECT mc.slug, mc.name, sc.slug, sc.name,
               COUNT(p.product_id) FILTER (WHERE p.is_deleted = false)
        FROM tbl_menu_category mc
        JOIN tbl_menu_sub_category sc ON sc.menu_category_id = mc.id AND sc.is_deleted = false
        LEFT JOIN tbl_menu_products p ON p.sub_category_id = sc.id
        WHERE mc.menu_id = %s AND mc.is_deleted = false
          AND mc.slug = ANY(%s)
        GROUP BY mc.sort_order, mc.slug, mc.name, sc.sort_order, sc.slug, sc.name
        ORDER BY mc.sort_order, sc.sort_order, sc.slug
        """,
        (menu_id, [slug for slug, _, _ in NEW_MAINS]),
    )
    print(f"[{label}] final drink tree:")
    for row in cur.fetchall():
        print(" ", row)

    cur.close()
    conn.close()


if __name__ == "__main__":
    setup("PROD", PROD)
    setup("STAGE", STAGE)
