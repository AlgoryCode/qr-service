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

TARGETS = [
    ("PROD", PROD, 17, 21),
    ("STAGE", STAGE, 9, 28),
]

PDF_CATEGORIES: list[tuple[str, str, list[tuple[str, float | None]]]] = [
    (
        "soguk_mezeler",
        "Soğuk Mezeler",
        [
            ("haydari", 350),
            ("yaprak sarma", 370),
            ("humus", 350),
            ("atom", 350),
            ("acili ezme", 350),
            ("zeytin tabagi", 350),
            ("girit ezmesi", 350),
            ("cevizli kabak", 350),
            ("soslu patlican", 350),
            ("kozde biber", 350),
            ("ahtapot salatasi", 1100),
            ("hamsi soslu", 950),
            ("karides sogus", 1100),
            ("uskumru marin", 950),
            ("istiridye", 2500),
            ("oyster", 2500),
            ("midye dolma", 520),
            ("beyaz peynir", 350),
            ("ordovr tabagi", 2050),
        ],
    ),
    (
        "ara_sicaklar",
        "Ara Sıcaklar",
        [
            ("balik corbasi", 420),
            ("sigara boregi", 470),
            ("ahtapot", 1800),
            ("kalamar izgara", 1050),
            ("karides guvec", 1050),
            ("jumbo karides", 2500),
            ("gunun corbasi", 320),
            ("patates tava", 410),
            ("=french fries", 410),
            ("sebze tabagi", 950),
            ("kalamar tava", 1050),
            ("kalamar guvec", 1050),
            ("sicak deniz urunleri", 5100),
        ],
    ),
    (
        "salatalar",
        "Salatalar",
        [
            ("coban salata", 510),
            ("ton balikli salata", 750),
            ("deniz urunleri salatasi", 1250),
            ("yunan salata", 610),
            ("mevsim salata", 510),
            ("sezar salata", 770),
        ],
    ),
    (
        "testi_kebap",
        "Testi Kebap",
        [
            ("testi kebap kuzu 150", 2000),
            ("testi kebap kuzu 300", 4000),
            ("testi kebap et 150", 2000),
            ("testi kebap et 300", 4000),
            ("testi kebap tavuk 150", 1900),
            ("testi kebap tavuk 300", 3200),
        ],
    ),
    (
        "etler",
        "Etler",
        [
            ("adana", 860),
            ("sarma beyti", 1030),
            ("tavuk sis", 770),
            ("tavuk snitzel", 770),
            ("kofte", 910),
            ("tavuk kanat", 810),
            ("et sis", 1100),
            ("pirzola", 1750),
            ("patlicanli kebap", 990),
            ("kuzu incik", 2100),
            ("tavuk doner", 750),
            ("et doner", 860),
            ("tavuk kori soslu", 770),
            ("ali nazik", 1060),
            ("sac tava", 1400),
            ("iskender", 990),
            ("tavuk sote", 850),
            ("et guvec", 1050),
            ("metrelik kebap", 3100),
            ("karisik kebap 2", 3400),
            ("karisik kebap 3", 5100),
            ("karisik kebap 4", 6500),
            ("kuzu kaburga", 6200),
            ("hamburger", 670),
            ("cheeseburger", 670),
            ("chicken nugget", 670),
            ("elsa anna", 670),
            ("princess cinderella", 670),
            ("spiderman", 670),
            ("batman", 670),
            ("mickey mouse", 670),
            ("satobiryan", None),
        ],
    ),
    (
        "turk_mutfagi",
        "Türk Mutfağı",
        [
            ("t-bone", 2100),
            ("dallas steak", 2200),
            ("biberli steak", 1700),
            ("mantarli steak", 1700),
            ("soganli steak", 1700),
            ("antrikot", 1700),
            ("etli fajita", 1700),
            ("tavuk fajita", 1250),
        ],
    ),
    (
        "baliklar",
        "Balıklar",
        [
            ("cupra", 1060),
            ("bream", 1060),
            ("levrek", 1060),
            ("bass", 1060),
            ("somon", 1100),
            ("salmon", 1100),
            ("tekir red mullet", 1300),
            ("red mullet", 1300),
            ("balik sis", 1120),
            ("kiremitte balik", 1180),
            ("kalkan porsiyon", 2100),
            ("balik kavurma", 1180),
            ("istavrit", 950),
            ("horse mackerel", 950),
            ("hamsi", 950),
            ("anchovy", 950),
            ("deniz levregi", 3100),
            ("seabass", 3100),
            ("deniz cuprasi", 3100),
            ("seabream", 3100),
            ("kalkan / turbot", 4600),
            ("kalkan firin", 4800),
            ("tuzda balik", 5200),
            ("tandir", 4200),
            ("tandoori", 4200),
            ("karisik balik 2", 3400),
            ("karisik balik 3", 5200),
            ("karisik balik 4", 6500),
            ("istakoz", 9500),
            ("lobster", 9500),
        ],
    ),
    (
        "pide_pizza",
        "Pide & Pizza",
        [
            ("kavurmali kasarli pide", 890),
            ("kasarli pide", 660),
            ("kiymali pide", 790),
            ("sucuklu pide", 790),
            ("karisik pide", 890),
            ("tavuklu pide", 680),
            ("pizza margarita", 850),
            ("paradise pizza", 970),
        ],
    ),
    (
        "makarnalar",
        "Makarnalar",
        [
            ("deniz mah", 2250),
            ("napoliten spaghetti", 680),
            ("bolonez spaghetti", 680),
            ("fettuccine alfredo", 710),
            ("penne arabiata", 620),
            ("manti", 710),
        ],
    ),
    (
        "tatlilar",
        "Tatlılar",
        [
            ("baklava", 520),
            ("sicak helva", 570),
            ("kunefe", 550),
            ("katmer", 550),
            ("dondurma", 600),
            ("ice cream", 600),
            ("mevsim meyvesi", 850),
            ("season fruits", 850),
        ],
    ),
]

OBSOLETE_MAIN_SLUGS = {
    "corbalar",
    "deniz_urunleri",
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
    text = text.replace("ı", "i").replace("İ", "i").replace("ş", "s").replace("Ş", "s")
    text = text.replace("ğ", "g").replace("Ğ", "g").replace("ü", "u").replace("Ü", "u")
    text = text.replace("ö", "o").replace("Ö", "o").replace("ç", "c").replace("Ç", "c")
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


DRINK_HINTS = (
    " cl",
    "double",
    "wine",
    "sarap",
    "bira",
    "beer",
    "vodka",
    "raki",
    "whiskey",
    "whisky",
    "viski",
    "coffee",
    "kahve",
    "cay",
    "tea",
    " su",
    "water",
    "cola",
    "fanta",
    "sprite",
    "juice",
    "ayran",
    "espresso",
    "latte",
    "cappuccino",
    "mojito",
    "margarita",
    "prosecco",
    " gin",
    " rum",
    "tequila",
    "likor",
    "baileys",
    "aperol",
    "redbull",
    "tonic",
    "nescafe",
    "epico",
    "sarafin",
    " thia",
    "besi bir yerde",
    "yeni raki",
    "efe gold",
    "tekirdag",
    "belvedere",
    "smirnoff",
    "absolut",
    "gilbey",
    "istanbul blue",
    "chivas",
    "jack daniel",
    "johnnie walker",
    "heineken",
    "corona",
    "miller",
    "bomonti",
    "efes",
    "beck",
    "guinness",
    "clausthaler",
    "brandy",
    "martini",
    "cosmopolitan",
    "jagermeister",
    "sex on the beach",
    "pina colada",
    "long island",
    "kup ",
    "mineral water",
    "lemonade",
    "pepsi",
    "americano",
    "turkish coffee",
)

PHOTO_DRINK_PRICES: list[tuple[str, float]] = [
    ("yeni raki double", 690),
    ("yeni raki 100", 4055),
    ("yeni raki 70", 3125),
    ("yeni raki 50", 2250),
    ("yeni raki 35", 1750),
    ("yeni raki 20", 1565),
    ("efe gold 100", 4375),
    ("efe gold 70", 3500),
    ("efe gold 50", 2500),
    ("efe gold 35", 2250),
    ("efe gold 20", 1690),
    ("tekirdag 100", 4375),
    ("tekirdag 70", 3500),
    ("tekirdag 50", 2500),
    ("tekirdag 35", 2250),
    ("tekirdag 20", 1690),
    ("belvedere 70", 7765),
    ("istanbul blue double", 690),
    ("istanbul blue 100", 3750),
    ("istanbul blue 70", 3125),
    ("istanbul blue 35", 1750),
    ("absolut double", 750),
    ("absolut 70", 4065),
    ("absolut 35", 2190),
    ("smirnoff double", 750),
    ("smirnoff 70", 4065),
    ("smirnoff 35", 2190),
    ("gilbey 100", 3750),
    ("gilbey 70", 3125),
    ("gilbey 35", 1750),
    ("gilbey double", 690),
    ("johnnie walker red label double", 875),
    ("johnnie walker red label 70", 5125),
    ("johnnie walker red label 35", 2815),
    ("chivas regal 18 70", 9875),
    ("chivas regal 12 double", 1000),
    ("chivas regal 12 70", 6250),
    ("chivas regal 12 35", 3375),
    ("jack daniel double", 1000),
    ("jack daniel 70", 6250),
    ("jack daniel 35", 3375),
    ("pepsi cola", 190),
    ("fanta 33", 190),
    ("sprite 33", 190),
    ("tonic water", 190),
    ("ice tea", 190),
    ("redbull", 350),
    ("lemonade", 190),
    ("orange juice", 250),
    ("fruit juice", 190),
    ("ayran", 160),
    ("mineral water 1", 300),
    ("mineral water 33", 150),
    ("water 1.5", 210),
    ("water 50", 90),
    ("cup of tea", 110),
    ("apple tea", 110),
    ("nescafe", 250),
    ("turkish coffee", 250),
    ("cappuccino", 310),
    ("espresso", 290),
    ("americano", 290),
    ("cafe latte", 310),
    ("red wine", 400),
    ("white wine", 400),
    ("rose wine", 400),
    ("prosecco sparkling wine glass", 1250),
    ("prosecco sparkling wine 750", 6240),
    ("efes pilsen 33", 230),
    ("efes pilsen 50", 250),
    ("efes pilsen bottle", 270),
    ("miller 33", 270),
    ("beck", 280),
    ("bomonti", 270),
    ("corona", 350),
    ("heineken", 350),
    ("clausthaler", 250),
    ("guinness", 450),
    ("brandy", 845),
    ("cosmopolitan", 845),
    ("aperol", 845),
    ("=vodka", 690),
    ("=raki", 690),
    ("=rum", 845),
    ("=wine", 550),
    ("jagermeister", 475),
    ("tequila", 475),
    ("baileys", 845),
    ("=gin", 690),
    ("mojito", 875),
    ("sex on the beach", 875),
    ("=martini", 690),
    ("long island", 875),
    ("pina colada", 845),
    ("=margarita", 845),
    ("besi bir yerde", 2000),
    ("thia blush", 2400),
    ("kup cherry", 2400),
    ("kup pomegranate", 2400),
    ("thia merlot", 2400),
    ("thia shiraz semi", 2400),
    ("thia shiraz", 2400),
    ("thia cabernet", 2400),
    ("thia chardonnay", 2400),
    ("thia sauvignon", 2400),
    ("epico shiraz", 2800),
    ("epico merlot", 2800),
    ("epico chardonnay", 2800),
    ("epico sauvignon", 2800),
    ("sarafin merlot", 4550),
    ("sarafin shiraz", 4800),
    ("sarafin chardonnay", 4800),
    ("sarafin sauvignon", 4800),
]


SHORT_TOKEN_HINTS = frozenset(
    {
        "tea",
        "su",
        "gin",
        "rum",
        "cola",
        "cay",
    }
)


def is_drink_name(name: str) -> bool:
    normalized = normalize(name)
    if "pizza" in normalized:
        return False
    tokens = set(normalized.split())
    padded = f" {normalized} "
    for hint in DRINK_HINTS:
        hint_norm = normalize(hint.strip())
        if not hint_norm:
            continue
        if hint_norm == "margarita":
            if "pizza" not in normalized and hint_norm in normalized:
                return True
            continue
        if hint_norm in SHORT_TOKEN_HINTS:
            if hint_norm in tokens or f" {hint_norm} " in padded:
                return True
            continue
        if hint_norm in normalized:
            return True
    return False


def classify_drink_sub(name: str) -> str:
    n = normalize(name)
    if any(k in n for k in ("coca", "fanta", "sprite", "ice tea", "ayran", "limonata", "tonic", "soda", " su", "water", "pellegrino", "portakal", "redbull", "lemonade", "pepsi", "juice", "fruit juice")):
        return "soguk_icecekler"
    if any(k in n for k in ("cay", "kahve", "nescafe", "espresso", "cappuccino", "americano", "latte", "turkish coffee", "cup of tea")):
        return "sicak_icecekler"
    if any(k in n for k in ("margarita", "mojito", "cosmopolitan", "collins", "spritz", "colada", "sangria", "bloody", "sex on", "white russian", "aperol", "pina colada", "long island")):
        return "kokteyller"
    if any(k in n for k in ("baileys", "jager", "jagermeister", "kahlua", "cointreau", "malibu", "limoncello", "likor")):
        return "likorler"
    if " gin" in n or n.startswith("gin"):
        return "gin"
    if "tequila" in n or "tekila" in n:
        return "tekila"
    if any(k in n for k in ("hennessy", "konyak", "cognac", "brandy")):
        return "konyak"
    if any(k in n for k in ("absolut", "grey goose", "istanbul blue", "smirnoff", "vodka", "belvedere")):
        return "vodkalar"
    if any(k in n for k in ("chivas", "talisker", "ballantine", "glenlivet", "jack daniel", "johnnie walker", "dimple", "jameson", "viski", "whiskey", "whisky")):
        return "viskiler"
    if any(k in n for k in ("yeni raki", "beylerbeyi", "tekirdag", "efe gold", " raki", " rakı")):
        return "rakilar"
    if any(k in n for k in ("efes", "bomonti", "miller", "heineken", "corona", "beck", "guinness", "clausthaler", " bira")):
        return "biralar"
    if any(k in n for k in ("prosecco", "moet", "chandon", "sampanya", "champagne")):
        return "sampanya_prosecco"
    if any(k in n for k in ("roze", "blush", "rose wine", "rose dry", "rose semi")):
        return "roze_saraplar"
    if any(k in n for k in ("yari tatli", "semi sweet")):
        return "yari_tatli_saraplar"
    if any(k in n for k in ("beyaz", "chardonnay", "sauvignon", "narince", "white wine")):
        return "beyaz_saraplar"
    if any(k in n for k in ("kirmizi", "cabernet", "merlot", "okuzgozu", "bogazkere", "shiraz", "red wine", "epico", "sarafin", " thia", "besi bir yerde", "kup ", " wine")):
        return "kirmizi_saraplar"
    return "alkollu_icecekler"


def load_drink_subs(cur, menu_id: int, drink_main_id: int) -> dict[str, int]:
    cur.execute(
        """
        SELECT slug, id
        FROM tbl_menu_sub_category
        WHERE menu_id = %s AND menu_category_id = %s AND is_deleted = false
        """,
        (menu_id, drink_main_id),
    )
    return {slug: sub_id for slug, sub_id in cur.fetchall()}


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


def upsert_default_sub(cur, menu_id: int, main_id: int, slug: str, name: str) -> int:
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
            SET menu_category_id = %s, name = %s, updated_at = %s
            WHERE id = %s
            """,
            (main_id, name, now, row[0]),
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
        RETURNING id
        """,
        (new_id, menu_id, main_id, slug, name, now, now),
    )
    return cur.fetchone()[0]


def match_product(name: str, pattern: str) -> bool:
    normalized_name = normalize(name)
    if pattern.startswith("="):
        return normalized_name == normalize(pattern[1:])
    normalized_pattern = normalize(pattern)
    return normalized_pattern in normalized_name


def photo_drink_price(name: str) -> float | None:
    for pattern, price in PHOTO_DRINK_PRICES:
        if match_product(name, pattern):
            return price
    return None


def setup_menu(label: str, cfg: dict, menu_id: int, user_id: int) -> None:
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()

    main_ids: dict[str, int] = {}
    sub_ids: dict[str, int] = {}

    for index, (slug, name, _products) in enumerate(PDF_CATEGORIES):
        main_ids[slug] = upsert_main(cur, menu_id, user_id, slug, name, index)
        sub_ids[slug] = upsert_default_sub(cur, menu_id, main_ids[slug], slug, name)

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
        SELECT product_id, name, price
        FROM tbl_menu_products
        WHERE menu_id = %s AND is_deleted = false
        """,
        (menu_id,),
    )
    products = cur.fetchall()

    assigned: set[int] = set()
    reassigned = 0
    price_updates = 0

    drink_main = upsert_main(cur, menu_id, user_id, "icecekler", "İçecekler", len(PDF_CATEGORIES))
    drink_subs = load_drink_subs(cur, menu_id, drink_main)
    if "alkollu_icecekler" not in drink_subs:
        drink_subs["alkollu_icecekler"] = upsert_default_sub(
            cur, menu_id, drink_main, "alkollu_icecekler", "Alkollü İçecekler"
        )
    default_drink_sub = next(iter(drink_subs.values()))

    drink_sort = 0
    for product_id, name, _price in products:
        if not is_drink_name(name):
            continue
        sub_slug = classify_drink_sub(name)
        target_sub = drink_subs.get(sub_slug, drink_subs.get("alkollu_icecekler", default_drink_sub))
        drink_price = photo_drink_price(name)
        if drink_price is not None:
            cur.execute(
                """
                UPDATE tbl_menu_products
                SET sub_category_id = %s, sort_order = %s, price = %s, updated_at = %s
                WHERE product_id = %s
                """,
                (target_sub, drink_sort * 10, drink_price, datetime.utcnow(), product_id),
            )
            price_updates += 1
        else:
            cur.execute(
                """
                UPDATE tbl_menu_products
                SET sub_category_id = %s, sort_order = %s, updated_at = %s
                WHERE product_id = %s
                """,
                (target_sub, drink_sort * 10, datetime.utcnow(), product_id),
            )
        drink_sort += 1
        assigned.add(product_id)
        reassigned += 1

    for category_slug, _category_name, category_products in PDF_CATEGORIES:
        target_sub = sub_ids[category_slug]
        for sort_index, (pattern, pdf_price) in enumerate(category_products):
            for product_id, name, current_price in products:
                if product_id in assigned:
                    continue
                if not match_product(name, pattern):
                    continue
                assigned.add(product_id)
                updates = ["sub_category_id = %s", "sort_order = %s", "updated_at = %s"]
                values: list[object] = [target_sub, sort_index * 10, datetime.utcnow()]
                if pdf_price is not None and float(current_price) != pdf_price:
                    updates.append("price = %s")
                    values.append(pdf_price)
                    price_updates += 1
                values.append(product_id)
                cur.execute(
                    f"UPDATE tbl_menu_products SET {', '.join(updates)} WHERE product_id = %s",
                    values,
                )
                reassigned += 1

    unassigned = [row for row in products if row[0] not in assigned]
    if unassigned:
        fallback_sub = sub_ids["etler"]
        for product_id, name, _price in unassigned:
            cur.execute(
                """
                UPDATE tbl_menu_products
                SET sub_category_id = %s, updated_at = %s
                WHERE product_id = %s
                """,
                (fallback_sub, datetime.utcnow(), product_id),
            )
            reassigned += 1
            print(f"[{label}] fallback etler: {name}")

    conn.commit()

    cur.execute(
        """
        SELECT slug, name, sort_order
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (menu_id,),
    )
    print(f"[{label}] menu {menu_id}: reassigned={reassigned} price_updates={price_updates}")
    for slug, name, sort_order in cur.fetchall():
        cur.execute(
            """
            SELECT COUNT(*)
            FROM tbl_menu_products p
            JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
            JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
            WHERE p.menu_id = %s AND mc.slug = %s AND p.is_deleted = false
            """,
            (menu_id, slug),
        )
        count = cur.fetchone()[0]
        print(f"  {sort_order:2} {slug:16} {count:3} urun")

    cur.close()
    conn.close()


def main() -> None:
    for label, cfg, menu_id, user_id in TARGETS:
        check = psycopg2.connect(**cfg)
        check_cur = check.cursor()
        check_cur.execute(
            "SELECT 1 FROM tbl_menu WHERE menu_id = %s AND is_deleted = false",
            (menu_id,),
        )
        if check_cur.fetchone() is None:
            print(f"[{label}] menu {menu_id} not found; skipped")
            check_cur.close()
            check.close()
            continue
        check_cur.close()
        check.close()
        setup_menu(label, cfg, menu_id, user_id)


if __name__ == "__main__":
    main()
