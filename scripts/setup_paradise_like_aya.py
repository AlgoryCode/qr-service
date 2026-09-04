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

SOURCE_MENU_ID = 16
TARGETS = [
    ("PROD", PROD, 17, 21),
    ("STAGE", STAGE, 9, 28),
]

MAINS = [
    ("corbalar", "Çorbalar"),
    ("soguk_mezeler", "Soğuk Mezeler"),
    ("salatalar", "Salatalar"),
    ("ara_sicaklar", "Ara Sıcaklar"),
    ("baliklar", "Balıklar"),
    ("etler", "Etler"),
    ("deniz_urunleri", "Deniz Ürünleri"),
    ("makarnalar", "Makarnalar"),
    ("tatlilar", "Tatlılar"),
    ("icecekler", "İçecekler"),
]

ICECEK_SUBS = [
    ("soguk_icecekler", "Soğuk"),
    ("sicak_icecekler", "Sıcak İçecekler"),
    ("beyaz_saraplar", "Beyaz Şaraplar"),
    ("kirmizi_saraplar", "Kırmızı Şaraplar"),
    ("roze_saraplar", "Roze Şaraplar"),
    ("yari_tatli_saraplar", "Yarı Tatlı Şaraplar"),
    ("sampanya_prosecco", "Şampanya / Prosecco"),
    ("biralar", "Biralar"),
    ("rakilar", "Rakılar"),
    ("vodkalar", "Vodkalar"),
    ("viskiler", "Viskiler"),
    ("kokteyller", "Kokteyller"),
    ("likorler", "Likörler"),
    ("gin", "Gin"),
    ("tekila", "Tekila"),
    ("konyak", "Konyak"),
]

DEFAULT_SUB = {
    "corbalar": ("corbalar", "Çorbalar"),
    "soguk_mezeler": ("soguk_mezeler", "Soğuk Mezeler"),
    "salatalar": ("salatalar", "Salatalar"),
    "ara_sicaklar": ("ara_sicaklar", "Ara Sıcaklar"),
    "baliklar": ("baliklar", "Balıklar"),
    "deniz_urunleri": ("deniz_urunleri", "Deniz Ürünleri"),
    "etler": ("etler", "Etler"),
    "makarnalar": ("makarnalar", "Makarnalar"),
    "tatlilar": ("tatlilar", "Tatlılar"),
}

OBSOLETE_MAIN_SLUGS = {
    "baslangiclar",
    "mezeler",
    "ana_yemekler",
    "pideler",
    "pizzalar",
    "burgerler",
    "durum_ve_doner",
    "izgaralar",
    "atistirmaliklar",
    "kokteyller",
    "cocuk_menusu",
    "meksika_mutfagi",
    "steakhouse",
}


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", text.lower())
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.replace("ı", "i").replace("İ", "i")
    return re.sub(r"\s+", " ", text).strip()


def classify_drink(name: str) -> str:
    n = normalize(name)
    if any(k in n for k in ("coca", "fanta", "sprite", "ice tea", "ayran", "serbet", "limonata", "tonic", "soda", " su", "pellegrino", "portakal suyu")):
        return "soguk_icecekler"
    if any(k in n for k in ("cay", "kahve", "nescafe", "espresso", "cappuccino", "americano", "latte", "bitki")):
        return "sicak_icecekler"
    if any(k in n for k in ("margarita", "mojito", "cosmopolitan", "collins", "spritz", "colada", "sangria", "bloody", "cuba", "island", "sex on", "white russian", "aperol", "tom collins", "pina")):
        return "kokteyller"
    if any(k in n for k in ("safari", "baileys", "archer", "jager", "kahlua", "cointreau", "malibu", "limoncello")):
        return "likorler"
    if any(k in n for k in ("hendrick", "gordon", "beefeater", "tanqueray", "bombay", " gin")):
        return "gin"
    if any(k in n for k in ("patron", "olmeca", "tequila", "tekila")):
        return "tekila"
    if any(k in n for k in ("hennessy", "henney", "remy", "martell", "konyak", "cognac")):
        return "konyak"
    if any(k in n for k in ("absolut", "grey goose", "istanbulblue", "istanblue", "vodka")):
        return "vodkalar"
    if any(k in n for k in ("chivas", "talisker", "ballantine", "glenlivet", "jack daniel", "dimple", "jameson", "viski", "whiskey")):
        return "viskiler"
    if any(k in n for k in ("yeni raki", "beylerbeyi", "tekirdag", " raki", " rakı")):
        return "rakilar"
    if any(k in n for k in ("efes", "bomonti", "miller", " bira")):
        return "biralar"
    if any(k in n for k in ("prosecco", "moet", "chandon", "dogarina", "ruffino", "sampanya", "champagne")):
        return "sampanya_prosecco"
    if any(k in n for k in ("roze", "blush")):
        return "roze_saraplar"
    if any(k in n for k in ("beyaz", "chardonnay", "sauvignon", "narince", "antre beyaz")):
        return "beyaz_saraplar"
    if any(k in n for k in ("kirmizi", "cabernet", "merlot", "okuzgozu", "bogazkere", "cardinale", "consensus")):
        return "kirmizi_saraplar"
    return "alkollu_icecekler"


def resolve_aya_main(main_slug: str, sub_slug: str, product_name: str) -> str:
    if main_slug in {"icecekler", "kokteyller"}:
        return "icecekler"
    if main_slug == "corbalar":
        return "corbalar"
    if main_slug == "mezeler":
        return "soguk_mezeler"
    if main_slug == "baslangiclar":
        return "salatalar" if sub_slug == "salatalar" else "ara_sicaklar"
    if main_slug == "atistirmaliklar":
        return "ara_sicaklar"
    if main_slug == "deniz_urunleri":
        if sub_slug == "balik_cesitleri" or "balik" in normalize(product_name):
            return "baliklar"
        return "deniz_urunleri"
    if main_slug in {"pideler", "pizzalar"}:
        return "makarnalar"
    if main_slug in {"ana_yemekler", "izgaralar", "steakhouse", "durum_ve_doner", "burgerler", "cocuk_menusu"}:
        if "makarna" in sub_slug:
            return "makarnalar"
        return "etler"
    if main_slug == "meksika_mutfagi":
        return "ara_sicaklar"
    if main_slug == "tatlilar":
        return "tatlilar"
    return "etler"


def upsert_main(cur, menu_id: int, user_id: int, slug: str, name: str, sort_order: int) -> int:
    cur.execute(
        """
        SELECT id FROM tbl_menu_category
        WHERE menu_id = %s AND slug = %s AND is_deleted = false
        """,
        (menu_id, slug),
    )
    row = cur.fetchone()
    now = datetime.utcnow()
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET name = %s, sort_order = %s, updated_at = %s
            WHERE id = %s
            """,
            (name, sort_order, now, row[0]),
        )
        return row[0]
    cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_category")
    new_id = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO tbl_menu_category (id, menu_id, user_id, slug, name, sort_order, created_at, updated_at, is_deleted)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, false)
        RETURNING id
        """,
        (new_id, menu_id, user_id, slug, name, sort_order, now, now),
    )
    return cur.fetchone()[0]


def upsert_sub(cur, menu_id: int, main_id: int, slug: str, name: str, sort_order: int) -> int:
    cur.execute(
        """
        SELECT id FROM tbl_menu_sub_category
        WHERE menu_id = %s AND slug = %s AND is_deleted = false
        """,
        (menu_id, slug),
    )
    row = cur.fetchone()
    now = datetime.utcnow()
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET menu_category_id = %s, name = %s, sort_order = %s, updated_at = %s
            WHERE id = %s
            """,
            (main_id, name, sort_order, now, row[0]),
        )
        return row[0]
    cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_sub_category")
    new_id = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO tbl_menu_sub_category (id, menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, false)
        RETURNING id
        """,
        (new_id, menu_id, main_id, slug, name, sort_order, now, now),
    )
    return cur.fetchone()[0]


def copy_category_covers(source_cur, target_cur, target_menu_id: int, main_ids: dict[str, int]) -> None:
    source_cur.execute(
        """
        SELECT slug, image_url, image_key
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        """,
        (SOURCE_MENU_ID,),
    )
    covers = {slug: (image_url, image_key) for slug, image_url, image_key in source_cur.fetchall() if image_url}
    now = datetime.utcnow()
    for slug, category_id in main_ids.items():
        if slug not in covers:
            continue
        image_url, image_key = covers[slug]
        target_cur.execute(
            """
            UPDATE tbl_menu_category
            SET image_url = %s, image_key = %s, updated_at = %s
            WHERE id = %s
            """,
            (image_url, image_key, now, category_id),
        )


def setup_menu(label: str, cfg: dict, menu_id: int, user_id: int, source_cur) -> None:
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()

    main_ids: dict[str, int] = {}
    sub_ids: dict[str, int] = {}

    for idx, (slug, name) in enumerate(MAINS):
        main_ids[slug] = upsert_main(cur, menu_id, user_id, slug, name, idx)

    for idx, (slug, name) in enumerate(ICECEK_SUBS):
        sub_ids[slug] = upsert_sub(cur, menu_id, main_ids["icecekler"], slug, name, idx)

    if "alkollu_icecekler" not in sub_ids:
        sub_ids["alkollu_icecekler"] = upsert_sub(
            cur, menu_id, main_ids["icecekler"], "alkollu_icecekler", "Alkollü İçecekler", 99
        )

    for main_slug, (sub_slug, sub_name) in DEFAULT_SUB.items():
        sub_ids[f"{main_slug}:{sub_slug}"] = upsert_sub(
            cur, menu_id, main_ids[main_slug], sub_slug, sub_name, 0
        )

    cur.execute(
        """
        UPDATE tbl_menu_category
        SET is_deleted = true, updated_at = %s
        WHERE menu_id = %s AND slug = ANY(%s) AND is_deleted = false
        """,
        (datetime.utcnow(), menu_id, list(OBSOLETE_MAIN_SLUGS)),
    )

    cur.execute(
        """
        SELECT p.product_id, p.name, mc.slug, sc.slug
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND p.is_deleted = false
        """,
        (menu_id,),
    )
    reassigned = 0
    for product_id, name, main_slug, sub_slug in cur.fetchall():
        aya_main = resolve_aya_main(main_slug, sub_slug, name)
        if aya_main == "icecekler":
            drink_sub = classify_drink(name)
            if main_slug == "kokteyller" and drink_sub == "alkollu_icecekler":
                drink_sub = "kokteyller"
            target_sub = sub_ids.get(drink_sub, sub_ids["alkollu_icecekler"])
        else:
            default = DEFAULT_SUB[aya_main]
            target_sub = sub_ids[f"{aya_main}:{default[0]}"]
        cur.execute(
            "UPDATE tbl_menu_products SET sub_category_id = %s, updated_at = %s WHERE product_id = %s",
            (target_sub, datetime.utcnow(), product_id),
        )
        reassigned += 1

    copy_category_covers(source_cur, cur, menu_id, main_ids)

    conn.commit()
    print(f"[{label}] menu {menu_id}: reassigned {reassigned} products")

    cur.execute(
        """
        SELECT slug, name, sort_order
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (menu_id,),
    )
    print(f"[{label}] categories:")
    for slug, name, sort_order in cur.fetchall():
        print(f"  {sort_order} {slug} ({name})")

    cur.close()
    conn.close()


def main() -> None:
    source_conn = psycopg2.connect(**PROD)
    source_cur = source_conn.cursor()
    for label, cfg, menu_id, user_id in TARGETS:
        check_conn = psycopg2.connect(**cfg)
        check_cur = check_conn.cursor()
        check_cur.execute("SELECT 1 FROM tbl_menu WHERE menu_id = %s AND is_deleted = false", (menu_id,))
        exists = check_cur.fetchone() is not None
        check_cur.close()
        check_conn.close()
        if not exists:
            print(f"[{label}] menu {menu_id} not found; skipped")
            continue
        setup_menu(label, cfg, menu_id, user_id, source_cur)
    source_cur.close()
    source_conn.close()


if __name__ == "__main__":
    main()
