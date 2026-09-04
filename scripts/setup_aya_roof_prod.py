from __future__ import annotations

import re
import unicodedata
import uuid
from datetime import datetime
from pathlib import Path

import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

MENU_ID = 16
USER_ID = 20
PUBLIC_BASE = "https://images.algorycode.com/qr-product-images"

MAINS = [
    ("corbalar", "Çorbalar"),
    ("soguk_mezeler", "Soğuk Mezeler"),
    ("salatalar", "Salatalar"),
    ("ara_sicaklar", "Ara Sıcaklar"),
    ("baliklar", "Balıklar"),
    ("deniz_urunleri", "Deniz Ürünleri"),
    ("etler", "Etler"),
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
    "kahvalti",
    "izgaralar",
    "kokteyller",
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
    if "mon reve" in n or "mon re" in n:
        return "kirmizi_saraplar"
    if any(k in n for k in ("chivas", "talisker", "ballantine", "glenlivet", "jack daniel", "dimple", "jameson", "viski", "whiskey")):
        return "viskiler"
    if any(k in n for k in ("yeni raki", "beylerbeyi", "tekirdag", " raki", " rakı")):
        return "rakilar"
    if any(k in n for k in ("efes", "bomonti", "miller", " bira")):
        return "biralar"
    if any(k in n for k in ("prosecco", "moet", "chandon", "dogarina", "ruffino", "sampanya", "champagne")):
        return "sampanya_prosecco"
    if any(k in n for k in ("vinkara", "domi", "yari tatli", "sec wine", "kalecik", "bornova misketi")):
        return "yari_tatli_saraplar"
    if any(k in n for k in ("roze", "blush")):
        return "roze_saraplar"
    if any(k in n for k in ("kirmizi", "cabernet", "merlot", "okuzgozu", "bogazkere", "cardinale", "consensus")) and "beyaz" not in n:
        if "kabatepe" in n and "beyaz" not in n and "sarafin sauvignon" not in n and "chardonnay" not in n:
            return "kirmizi_saraplar"
        if any(x in n for x in ("kirmizi", "cabernet", "merlot", "okuzgozu", "bogazkere", "cardinale", "consensus", "antre kirmizi")):
            return "kirmizi_saraplar"
    if any(k in n for k in ("beyaz", "chardonnay", "sauvignon", "narince", "antre beyaz")):
        return "beyaz_saraplar"
    if "kabatepe" in n and "kadeh" in n:
        return "kirmizi_saraplar"
    if "suvla kabatepe" in n and "sise" in n:
        return "kirmizi_saraplar"
    if "sarafin" in n and "sauvignon" in n:
        return "beyaz_saraplar"
    if "sarafin" in n and "chardonnay" in n:
        return "beyaz_saraplar"
    if "mon reve" in n and "chardonnay" in n:
        return "beyaz_saraplar"
    if "antre" in n and "beyaz" in n:
        return "beyaz_saraplar"
    if "suvla" in n and ("sauvignon" in n or "narince" in n):
        return "beyaz_saraplar"
    return "alkollu_icecekler"


def classify_food(name: str) -> tuple[str, str | None]:
    n = normalize(name)
    if any(k in n for k in ("corba", "çorba")):
        return "corbalar", None
    if any(k in n for k in ("salata", "salatasi")):
        return "salatalar", None
    if any(k in n for k in ("ezme", "haydari", "patlican", "yaprak sarma", "humus", "enginar", "somon tartine")):
        return "soguk_mezeler", None
    if any(k in n for k in ("kalamar", "borek", "karides", "ahtapot", "jumbo", "tempura", "hellim")):
        return "ara_sicaklar", None
    if any(k in n for k in ("istakoz", "kalkan", "karisik deniz urunleri")):
        return "deniz_urunleri", None
    if any(k in n for k in ("levrek", "cupra", "barbun", "palamut", "somon izgara", "balik", "bugulama", "tuzda balik", "karisik balik", "safranli balik", "deniz levregi", "deniz cupra")):
        return "baliklar", None
    if any(k in n for k in ("fettuccini", "fettucini", "spagetti", "manti", "pomodore", "makarna")):
        return "makarnalar", None
    if any(k in n for k in ("katmer", "dondurma", "sufle", "meyve", "panna cotta", "tatli", "ayva")):
        return "tatlilar", None
    if any(k in n for k in (
        "kebap", "kofte", "bonfile", "pirzola", "kuzu", "kaz kebab", "mutancana", "testi",
        "osmanli saray", "yogurtlu kebap", "karisik izgara", "tavuk", "adana", "kirde",
        "erikli kuzu", "kahvalti", "ordovr", "guvec",
    )):
        return "etler", None
    return "icecekler", classify_drink(name)


def upsert_main(cur, slug: str, name: str, sort_order: int) -> int:
    cur.execute(
        """
        SELECT id FROM tbl_menu_category
        WHERE menu_id = %s AND slug = %s AND is_deleted = false
        """,
        (MENU_ID, slug),
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
    cur.execute(
        """
        INSERT INTO tbl_menu_category (menu_id, user_id, slug, name, sort_order, created_at, updated_at, is_deleted)
        VALUES (%s, %s, %s, %s, %s, %s, %s, false)
        RETURNING id
        """,
        (MENU_ID, USER_ID, slug, name, sort_order, now, now),
    )
    return cur.fetchone()[0]


def upsert_sub(cur, main_id: int, slug: str, name: str, sort_order: int) -> int:
    cur.execute(
        """
        SELECT id FROM tbl_menu_sub_category
        WHERE menu_id = %s AND slug = %s AND is_deleted = false
        """,
        (MENU_ID, slug),
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
    cur.execute(
        """
        INSERT INTO tbl_menu_sub_category (menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
        VALUES (%s, %s, %s, %s, %s, %s, %s, false)
        RETURNING id
        """,
        (MENU_ID, main_id, slug, name, sort_order, now, now),
    )
    return cur.fetchone()[0]


def set_cover_from_file(cur, category_id: int, image_path: Path) -> None:
    if not image_path.exists():
        return
    object_key = f"menus/{MENU_ID}/categories/{category_id}/{uuid.uuid4()}.png"
    image_url = f"{PUBLIC_BASE}/{object_key}"
    cur.execute(
        """
        UPDATE tbl_menu_category
        SET image_url = %s, image_key = %s, updated_at = %s
        WHERE id = %s
        """,
        (image_url, object_key, datetime.utcnow(), category_id),
    )


def main() -> None:
    assets = Path(__file__).resolve().parents[2] / ".cursor" / "projects" / "c-Users-Tarik-Desktop-Services" / "assets"
    if not assets.exists():
        assets = Path(r"C:\Users\Tarik\.cursor\projects\c-Users-Tarik-Desktop-Services\assets")

    conn = psycopg2.connect(**PROD)
    conn.autocommit = False
    cur = conn.cursor()

    main_ids: dict[str, int] = {}
    sub_ids: dict[str, int] = {}

    for idx, (slug, name) in enumerate(MAINS):
        main_ids[slug] = upsert_main(cur, slug, name, idx)

    for idx, (slug, name) in enumerate(ICECEK_SUBS):
        sub_ids[slug] = upsert_sub(cur, main_ids["icecekler"], slug, name, idx)

    for main_slug, (sub_slug, sub_name) in DEFAULT_SUB.items():
        sub_ids[f"{main_slug}:{sub_slug}"] = upsert_sub(cur, main_ids[main_slug], sub_slug, sub_name, 0)

    cur.execute(
        """
        UPDATE tbl_menu_category
        SET is_deleted = true, updated_at = %s
        WHERE menu_id = %s AND slug = ANY(%s) AND is_deleted = false
        """,
        (datetime.utcnow(), MENU_ID, list(OBSOLETE_MAIN_SLUGS)),
    )

    cur.execute(
        """
        SELECT product_id, name FROM tbl_menu_products
        WHERE menu_id = %s AND is_deleted = false
        """,
        (MENU_ID,),
    )
    products = cur.fetchall()
    reassigned = 0
    for product_id, name in products:
        main_slug, drink_sub = classify_food(name)
        if main_slug == "icecekler":
            sub_slug = drink_sub or "alkollu_icecekler"
            if sub_slug not in sub_ids:
                sub_slug = "alkollu_icecekler"
                if "alkollu_icecekler" not in sub_ids:
                    sub_ids["alkollu_icecekler"] = upsert_sub(
                        cur, main_ids["icecekler"], "alkollu_icecekler", "Alkollü İçecekler", 99
                    )
            target_sub = sub_ids[sub_slug]
        else:
            default = DEFAULT_SUB[main_slug]
            target_sub = sub_ids[f"{main_slug}:{default[0]}"]
        cur.execute(
            "UPDATE tbl_menu_products SET sub_category_id = %s, updated_at = %s WHERE product_id = %s",
            (target_sub, datetime.utcnow(), product_id),
        )
        reassigned += 1

    cover_files = {
        "corbalar": assets / "aya-prod-cover-corbalar.png",
        "soguk_mezeler": assets / "aya-prod-cover-soguk-mezeler.png",
        "salatalar": assets / "aya-prod-cover-salatalar.png",
        "ara_sicaklar": assets / "aya-prod-cover-ara-sicaklar.png",
        "baliklar": assets / "aya-prod-cover-baliklar.png",
        "deniz_urunleri": assets / "aya-prod-cover-deniz-urunleri.png",
        "etler": assets / "aya-prod-cover-etler.png",
        "makarnalar": assets / "aya-prod-cover-makarnalar.png",
        "tatlilar": assets / "aya-prod-cover-tatlilar.png",
        "icecekler": assets / "aya-prod-cover-icecekler.png",
    }
    for slug, path in cover_files.items():
        set_cover_from_file(cur, main_ids[slug], path)

    conn.commit()
    print(f"Reassigned {reassigned} products")
    print("Main categories:")
    for slug, name in MAINS:
        print(f"  {main_ids[slug]:>3} sort={MAINS.index((slug, name))} {name} ({slug})")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
