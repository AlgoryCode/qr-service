from __future__ import annotations

import re
import unicodedata
from datetime import datetime, timezone
from pathlib import Path

import openpyxl
import psycopg2

EXCEL = Path(r"C:\Users\Tarik\Downloads\menu_urun_foto_linkleri (2).xlsx")
AYA_PROD_MENU = 16
PARADISE_MENU = 17
AYA_EMAIL = "reservationayaroof@gmail.com"

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

GENERIC_CORES = {
    "gin",
    "tequila",
    "brandy",
    "wine",
    "raki",
    "vodka",
    "rum",
    "martini",
    "appletea",
    "prosecosparklingwine",
    "gilbeys",
    "fruitjuice",
    "pepsicola",
    "redbull",
}

CORE_ALIASES = {
    "lemonade": "evyapimilimonata",
    "orangejuice": "sikmaportakalsuyu",
    "turkishcoffee": "turkkahvesi",
    "cupoftea": "fincancay",
    "bomontiunfiltered": "bomontifiltresiz",
    "efespilsen": "efes",
    "istanbulblue": "istanblue",
    "longislandicedtea": "longisland",
    "tonicwater": "tonic",
    "chivasregal12": "chivasregal12years",
    "chivasregal18": "chivasregal18years",
    "tekirdag": "tekirdagaltinseri",
    "mineralwater": "soda",
    "whitewine": "suvlakabatepe",
    "redwine": "kabatepekirmizi",
}

SIZE_ALIASES = {
    "150cl": "150cl",
    "100cl": "100cl",
    "75cl": "70cl",
    "70cl": "70cl",
    "50cl": "50cl",
    "35cl": "35cl",
    "33cl": "33cl",
    "20cl": "20cl",
    "5cl": "5cl",
    "double": "5cl",
    "glass": "glass",
}


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
    folded = folded.replace("double", " ")
    folded = folded.replace("unfiltered", " ").replace("filtresiz", " ")
    folded = folded.replace("pilsen", " ")
    folded = folded.replace("years", " ").replace("year", " ")
    folded = re.sub(r"\b\d+(?:\.\d+)?\s*(cl|ml|l|lt)\b", " ", folded)
    folded = re.sub(r"[()]", " ", folded)
    folded = re.sub(r"\s+", " ", folded).strip()
    key = normalize(folded)
    return CORE_ALIASES.get(key, key)


def canonical_size(text: str) -> str | None:
    folded = fold(text)
    if re.search(r"\bdouble\b", folded):
        return "5cl"
    if re.search(r"\b(kadeh|glass)\b", folded):
        return "glass"
    match = re.search(r"(\d+(?:\.\d+)?)\s*(cl|ml|lt|l)\b", folded)
    if not match:
        return None
    amount = float(match.group(1))
    unit = match.group(2)
    if unit == "ml":
        amount /= 10.0
    elif unit in {"l", "lt"}:
        amount *= 100.0
    cl_value = int(round(amount))
    raw = f"{cl_value}cl"
    return SIZE_ALIASES.get(raw, raw)


def years(text: str) -> set[str]:
    return set(re.findall(r"\b(12|15|18|21|25)\b", fold(text)))


def size_compatible(excel_name: str, aya_name: str) -> bool:
    excel_size = canonical_size(excel_name)
    aya_size = canonical_size(aya_name)
    aya_glass = bool(re.search(r"\b(kadeh|glass)\b", fold(aya_name)))
    if excel_size == "glass":
        return aya_glass
    if aya_glass:
        return False
    if excel_size and aya_size:
        return excel_size == aya_size
    return True


def water_target(excel_name: str) -> str | None:
    key = normalize(excel_name)
    mapping = {
        normalize("Water 1.5 l"): "buyuksu",
        normalize("Water 50 cl"): "kucuksu",
        normalize("Mineral Water 33 cl"): "kucuksoda",
        normalize("Mineral Water 1 l"): "buyuksoda",
    }
    return mapping.get(key)


def is_generic(excel_name: str) -> bool:
    core = core_key(excel_name)
    if core in GENERIC_CORES:
        return True
    if core.startswith("gilbeys"):
        return True
    return False


def score_pair(excel_name: str, aya_name: str) -> int:
    if is_generic(excel_name):
        return 0
    excel_years = years(excel_name)
    aya_years = years(aya_name)
    if excel_years and aya_years and excel_years.isdisjoint(aya_years):
        return 0
    if not size_compatible(excel_name, aya_name):
        return 0

    water = water_target(excel_name)
    aya_core = core_key(aya_name)
    if water:
        return 96 if aya_core == water else 0

    excel_core = core_key(excel_name)
    if not excel_core or not aya_core:
        return 0
    if excel_core == aya_core:
        excel_size = canonical_size(excel_name)
        aya_size = canonical_size(aya_name)
        if excel_size and aya_size and excel_size == aya_size:
            return 100
        if excel_size == "glass" and aya_size == "glass":
            return 92
        return 88
    if excel_core in aya_core or aya_core in excel_core:
        shorter = min(len(excel_core), len(aya_core))
        longer = max(len(excel_core), len(aya_core))
        if shorter >= 6 and shorter / longer >= 0.7:
            return 80
    return 0


def load_excel_drinks() -> list[tuple[str, str, str]]:
    workbook = openpyxl.load_workbook(EXCEL, data_only=True)
    rows: list[tuple[str, str, str]] = []
    for index, row in enumerate(workbook.active.iter_rows(values_only=True)):
        if index == 0:
            continue
        category = fold(str(row[0] or ""))
        if "icecek" not in category:
            continue
        name = str(row[2] or "").strip()
        link = str(row[3] or "").strip()
        if not name or not link:
            continue
        rows.append((str(row[1] or "").strip(), name, link))
    return rows


def load_menu_products(cfg: dict, menu_id: int) -> list[tuple[int, str, str | None, str, str]]:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT p.product_id, p.name, p.image_url, mc.slug, sc.slug
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND p.is_deleted = false
        ORDER BY p.product_id
        """,
        (menu_id,),
    )
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return rows


def load_aya_drinks(cfg: dict, menu_id: int) -> list[tuple[int, str, str | None, str, str]]:
    products = load_menu_products(cfg, menu_id)
    drinks = []
    for row in products:
        main_slug, sub_slug = row[3], row[4]
        if main_slug.startswith("drink-") or main_slug == "icecekler" or "icecek" in sub_slug:
            drinks.append(row)
    return drinks


def resolve_aya_menu_id(cfg: dict) -> int | None:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT m.menu_id
        FROM tbl_menu m
        JOIN tbl_user u ON u.id = m.user_id
        WHERE m.is_deleted = false
          AND (
            u.email = %s
            OR m.email ILIKE %s
            OR m.business_name ILIKE %s
          )
        ORDER BY m.menu_id
        LIMIT 1
        """,
        (AYA_EMAIL, "%ayaroof%", "%aya%roof%"),
    )
    row = cur.fetchone()
    cur.close()
    conn.close()
    return row[0] if row else None


def paradise_image_by_name(products: list[tuple[int, str, str | None, str, str]]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    for product_id, name, image_url, _main, _sub in products:
        if image_url and image_url.startswith("https://images.algorycode.com/"):
            mapping[normalize(name)] = image_url
    return mapping


def assign_matches(
    excel_rows: list[tuple[str, str, str]],
    aya_drinks: list[tuple[int, str, str | None, str, str]],
) -> tuple[list[tuple], list[str]]:
    candidates: list[tuple[int, tuple, tuple]] = []
    for excel_row in excel_rows:
        excel_name = excel_row[1]
        for aya_row in aya_drinks:
            score = score_pair(excel_name, aya_row[1])
            if score >= 80:
                candidates.append((score, excel_row, aya_row))
    candidates.sort(key=lambda item: (-item[0], item[1][1], item[2][1]))

    used_excel: set[str] = set()
    used_aya: set[int] = set()
    assigned: list[tuple] = []
    for score, excel_row, aya_row in candidates:
        if excel_row[1] in used_excel or aya_row[0] in used_aya:
            continue
        used_excel.add(excel_row[1])
        used_aya.add(aya_row[0])
        assigned.append((score, excel_row, aya_row))

    unmatched = [row[1] for row in excel_rows if row[1] not in used_excel]
    return assigned, unmatched


def update_product_image(cfg: dict, product_id: int, image_url: str) -> None:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        UPDATE tbl_menu_products
        SET image_url = %s, updated_at = %s
        WHERE product_id = %s
        """,
        (image_url, now(), product_id),
    )
    conn.commit()
    cur.close()
    conn.close()


def apply_assignments(
    cfg: dict,
    label: str,
    assignments: list[tuple],
    image_by_excel_name: dict[str, str],
) -> tuple[int, int]:
    updated = 0
    missing_image = 0
    for score, excel_row, aya_row in assignments:
        excel_name = excel_row[1]
        image_url = image_by_excel_name.get(normalize(excel_name))
        if not image_url:
            missing_image += 1
            print(f"{label} NOIMG {excel_name}")
            continue
        update_product_image(cfg, aya_row[0], image_url)
        updated += 1
        print(f"{label} [{score}] {aya_row[1]!r} <- {excel_name!r}")
    return updated, missing_image


def main(dry_run: bool = True) -> None:
    excel_rows = load_excel_drinks()
    print(f"excel drinks={len(excel_rows)}")

    paradise = load_menu_products(PROD, PARADISE_MENU)
    image_by_excel_name = paradise_image_by_name(paradise)
    print(f"paradise cdn images usable={len(image_by_excel_name)}")

    aya_prod = load_aya_drinks(PROD, AYA_PROD_MENU)
    assigned, unmatched = assign_matches(excel_rows, aya_prod)
    print(f"prod aya drinks={len(aya_prod)} matches={len(assigned)}")
    already = sum(1 for _score, _excel, aya in assigned if aya[2])
    print(f"matches already had image={already}")

    for score, excel_row, aya_row in assigned:
        had = "img" if aya_row[2] else "noimg"
        print(f"MATCH [{score}] {had} AYA {aya_row[0]} {aya_row[1]!r} <- {excel_row[1]!r}")

    print(f"unmatched excel drinks={len(unmatched)}")
    for name in unmatched:
        print(f"SKIP {name}")

    aya_unmatched = [
        row for row in aya_prod if row[0] not in {aya[0] for _s, _e, aya in assigned} and not row[2]
    ]
    print(f"aya drinks still without image after match={len(aya_unmatched)}")
    for row in aya_unmatched:
        print(f"AYA MISS {row[0]} {row[1]} [{row[3]}/{row[4]}]")

    stage_menu_id = resolve_aya_menu_id(STAGE)
    print(f"stage aya menu={stage_menu_id}")

    if dry_run:
        print("dry-run only")
        return

    prod_updated, prod_missing = apply_assignments(PROD, "PROD", assigned, image_by_excel_name)
    print(f"prod updated={prod_updated} missing_image={prod_missing}")

    if stage_menu_id is None:
        print("stage skip: Aya Roof menu yok")
        return

    stage_drinks = load_aya_drinks(STAGE, stage_menu_id)
    stage_assigned, _stage_unmatched = assign_matches(excel_rows, stage_drinks)
    stage_updated, stage_missing = apply_assignments(STAGE, "STAGE", stage_assigned, image_by_excel_name)
    print(f"stage updated={stage_updated} missing_image={stage_missing}")


if __name__ == "__main__":
    import sys

    main(dry_run="--apply" not in sys.argv)
