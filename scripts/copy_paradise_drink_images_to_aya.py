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

AYA_MENU = 16
PARADISE_MENU = 17

SIZE_RE = re.compile(
    r"(kadeh|sise|sis|bottle|glass|cl|ml|lt|l)\b|\d+",
    re.IGNORECASE,
)


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def fold(text: str) -> str:
    text = unicodedata.normalize("NFKD", (text or "").lower())
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return text.replace("ı", "i").replace("İ", "i")


def normalize(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", fold(text))


def core_key(text: str) -> str:
    folded = fold(text)
    folded = folded.replace("şişe", " ").replace("sise", " ")
    folded = folded.replace("kadeh", " ").replace("bottle", " ").replace("glass", " ")
    folded = re.sub(r"\b\d+\s*(cl|ml|l|lt)?\b", " ", folded)
    folded = re.sub(r"[()]", " ", folded)
    folded = re.sub(r"\s+", " ", folded).strip()
    return normalize(folded)


def load_alcoholic(cur, menu_id: int):
    cur.execute(
        """
        SELECT p.product_id, p.name, p.image_url, mc.slug, mc.name, p.sort_order
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s
          AND p.is_deleted = false
          AND (
            mc.slug LIKE 'drink-%%'
            OR mc.slug IN (
              'biralar', 'kadeh_icecekler', 'kokteyller', 'yabanci_ickiler',
              'kirmizi_saraplar', 'beyaz_saraplar', 'sparkling_wine', 'saraplar', 'rakilar'
            )
          )
          AND mc.slug NOT IN ('drink-cold-drinks', 'drink-hot-drinks', 'soguk_icecekler', 'sicak_icecekler')
        ORDER BY mc.sort_order, p.sort_order, p.product_id
        """,
        (menu_id,),
    )
    return cur.fetchall()


BRAND_ALIASES = (
    ("bomontifiltresiz", "bomontiunfiltered"),
    ("bomontiunfiltered", "bomontiunfiltered"),
    ("efespilsen", "efes"),
    ("yeniraki", "yeniraki"),
    ("tekirdagaltinseri", "tekirdag"),
    ("tekirdag", "tekirdag"),
    ("miller", "miller"),
    ("sarafinsauvignonblanc", "sarafinsauvignonblanc"),
    ("sarafinchardonnay", "sarafinchardonnay"),
    ("sarafinmerlot", "sarafinmerlot"),
    ("sarafincabernetsauvignon", "sarafincabernetsauvignon"),
)


def brand_parts(text: str) -> tuple[str, str]:
    key = core_key(text)
    size_matches = re.findall(r"\b(\d+)\s*(?:cl|ml)\b", fold(text))
    size_part = size_matches[0] if size_matches else ""
    for alias, canon in BRAND_ALIASES:
        if key == alias or key.startswith(alias):
            return canon, size_part
    return key, size_part


def year_numbers(text: str) -> set[str]:
    return set(re.findall(r"\b(12|15|18|21|25)\b", fold(text)))


def size_numbers(text: str) -> set[str]:
    return set(re.findall(r"\b(\d+)\s*(?:cl|ml)\b", fold(text)))


def score_pair(aya_name: str, par_name: str) -> int:
    a_years = year_numbers(aya_name)
    p_years = year_numbers(par_name)
    if a_years and p_years and a_years.isdisjoint(p_years):
        return 0

    a_exact = normalize(aya_name)
    p_exact = normalize(par_name)
    if a_exact == p_exact:
        return 100

    a_brand, a_size = brand_parts(aya_name)
    p_brand, p_size = brand_parts(par_name)
    if a_brand and a_brand == p_brand:
        if a_size and p_size and a_size == p_size:
            return 95
        return 88

    a_core = core_key(aya_name)
    p_core = core_key(par_name)
    if not a_core or not p_core:
        return 0
    if a_core == p_core:
        return 90

    a_sizes = size_numbers(aya_name)
    p_sizes = size_numbers(par_name)
    size_bonus = 5 if a_sizes and p_sizes and a_sizes & p_sizes else 0

    if a_core in p_core or p_core in a_core:
        shorter = min(len(a_core), len(p_core))
        longer = max(len(a_core), len(p_core))
        if shorter >= 5 and shorter / longer >= 0.5:
            return 75 + size_bonus

    a_tokens = set(re.findall(r"[a-z0-9]{3,}", fold(aya_name)))
    p_tokens = set(re.findall(r"[a-z0-9]{3,}", fold(par_name)))
    noise = {
        "sise",
        "kadeh",
        "bottle",
        "glass",
        "wine",
        "red",
        "white",
        "rose",
        "roze",
        "sarap",
        "years",
        "year",
        "double",
    }
    a_tokens -= noise
    p_tokens -= noise
    if not a_tokens or not p_tokens:
        return 0
    if a_tokens.issubset(p_tokens) and len(a_tokens) >= 2:
        return 80 + size_bonus
    if p_tokens.issubset(a_tokens) and len(p_tokens) >= 2:
        return 80 + size_bonus
    return 0


def best_paradise_match(aya_row, paradise_rows):
    best = None
    best_score = 0
    aya_sizes = size_numbers(aya_row[1])
    for par in paradise_rows:
        if not par[2]:
            continue
        score = score_pair(aya_row[1], par[1])
        if score < 75:
            continue
        par_sizes = size_numbers(par[1])
        tie_break = 1 if aya_sizes and par_sizes and aya_sizes & par_sizes else 0
        if score > best_score or (score == best_score and tie_break and best is not None and not (
            size_numbers(best[1]) & aya_sizes
        )):
            best_score = score
            best = par
    if best is None:
        return None, 0
    return best, best_score


def main(dry_run: bool = False) -> None:
    conn = psycopg2.connect(**PROD)
    cur = conn.cursor()
    paradise = load_alcoholic(cur, PARADISE_MENU)
    aya = load_alcoholic(cur, AYA_MENU)

    planned: list[tuple] = []
    for aya_row in aya:
        if aya_row[2]:
            continue
        match, score = best_paradise_match(aya_row, paradise)
        if match is None:
            continue
        planned.append((aya_row, match, score))

    print(f"planned transfers (AYA missing image): {len(planned)}")
    for aya_row, par_row, score in planned:
        print(f"[{score}] AYA {aya_row[0]} {aya_row[1]!r} <- PAR {par_row[0]} {par_row[1]!r}")

    if dry_run:
        print("dry-run only")
        cur.close()
        conn.close()
        return

    updated = 0
    for aya_row, par_row, _score in planned:
        cur.execute(
            """
            UPDATE tbl_menu_products
            SET image_url = %s, updated_at = %s
            WHERE product_id = %s AND menu_id = %s
            """,
            (par_row[2], now(), aya_row[0], AYA_MENU),
        )
        updated += 1
    conn.commit()
    print(f"updated={updated}")

    # refresh category covers for drink categories that now have images
    cur.execute(
        """
        SELECT mc.id, mc.slug, mc.name, mc.image_url
        FROM tbl_menu_category mc
        WHERE mc.menu_id = %s
          AND mc.is_deleted = false
          AND mc.slug LIKE 'drink-%%'
          AND (mc.image_url IS NULL OR mc.image_url = '')
        ORDER BY mc.sort_order
        """,
        (AYA_MENU,),
    )
    cats = cur.fetchall()
    cover_ok = 0
    for cat_id, slug, name, _ in cats:
        cur.execute(
            """
            SELECT p.image_url
            FROM tbl_menu_products p
            JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
            WHERE p.menu_id = %s
              AND sc.menu_category_id = %s
              AND p.is_deleted = false
              AND p.image_url IS NOT NULL
              AND p.image_url <> ''
            ORDER BY p.sort_order, p.product_id
            LIMIT 1
            """,
            (AYA_MENU, cat_id),
        )
        row = cur.fetchone()
        if not row:
            print(f"COVER SKIP {slug}: still no product image")
            continue
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET image_url = %s, updated_at = %s
            WHERE id = %s
            """,
            (row[0], now(), cat_id),
        )
        cover_ok += 1
        print(f"COVER {name} <- {row[0][:80]}")
    conn.commit()
    print(f"covers set={cover_ok}")
    cur.close()
    conn.close()


if __name__ == "__main__":
    import sys

    main(dry_run="--apply" not in sys.argv)
