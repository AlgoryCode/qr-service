#!/usr/bin/env python3
"""Upsert descriptor taxonomy and assign descriptor_category_id for all menu products."""

from __future__ import annotations

import argparse
import json
import os
import re
import unicodedata
from pathlib import Path
from typing import Any

import psycopg2
import psycopg2.extras

PROD = dict(
    host=os.environ.get("PROD_DB_HOST", "185.184.210.52"),
    port=int(os.environ.get("PROD_DB_PORT", "5432")),
    dbname=os.environ.get("PROD_DB_NAME", "algoryqrdb"),
    user=os.environ.get("PROD_DB_USERNAME", "postgres"),
    password=os.environ.get(
        "PROD_DB_PASSWORD",
        "AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    ),
)

STAGE = dict(
    host=os.environ.get("STAGE_DB_HOST", "185.184.210.52"),
    port=int(os.environ.get("STAGE_DB_PORT", "5433")),
    dbname=os.environ.get("STAGE_DB_NAME", "algoryqrdb-stage"),
    user=os.environ.get("STAGE_DB_USERNAME", "postgres"),
    password=os.environ.get("STAGE_DB_PASSWORD", "postgres_stage"),
)

DB_BY_ENV = {"prod": PROD, "stage": STAGE}

SEED_PATH = Path(__file__).resolve().parents[1] / "src/main/resources/seed/menu-taxonomy.json"

EXTRA_DESCRIPTOR_SPECS: list[dict[str, Any]] = [
    {"id": 1024, "subSlug": "sutlu_icecekler", "slug": "sutlu_latte", "name": "Latte", "sortOrder": 1},
    {"id": 1025, "subSlug": "sutlu_icecekler", "slug": "sutlu_sicak_cikolata", "name": "Sıcak Çikolata", "sortOrder": 2},
    {"id": 1026, "subSlug": "sutlu_icecekler", "slug": "sutlu_milkshake", "name": "Milkshake", "sortOrder": 3},
    {"id": 1027, "subSlug": "meyve_sulari", "slug": "taze_meyve_suyu", "name": "Taze Meyve Suyu", "sortOrder": 1},
    {"id": 1028, "subSlug": "soguk_icecekler", "slug": "ice_tea", "name": "Ice Tea", "sortOrder": 4},
    {"id": 1029, "subSlug": "fermente_icecekler", "slug": "salgam", "name": "Şalgam", "sortOrder": 2},
    {"id": 1030, "subSlug": "soguk_icecekler", "slug": "soguk_kahve", "name": "Soğuk Kahve", "sortOrder": 5},
    {"id": 1031, "subSlug": "su", "slug": "su_icecek", "name": "Su", "sortOrder": 1},
    {"id": 1032, "subSlug": "sutlu_icecekler", "slug": "sutlu_cappuccino", "name": "Cappuccino", "sortOrder": 4},
    {"id": 1033, "subSlug": "soguk_icecekler", "slug": "soguk_meyve_suyu", "name": "Meyve Suyu", "sortOrder": 6},
    {"id": 1034, "subSlug": "soguk_icecekler", "slug": "soguk_milkshake", "name": "Milkshake", "sortOrder": 7},
]

NAME_RULES: list[tuple[str, list[str]]] = [
    (
        "sarap",
        [
            r"\bşarap\b",
            r"\bsarap\b",
            r"\bwine\b",
            r"\bprosecco\b",
            r"\bchianti\b",
            r"\bmerlot\b",
            r"\bcabernet\b",
            r"\bshiraz\b",
            r"\bros[eé]\b",
            r"\bantre\b",
            r"\bcardinale\b",
            r"\bconsensus\b",
            r"\bdogarina\b",
            r"\bkadeh\b",
            r"\b(kırmızı|kirmizi|beyaz|roze|rose|blush)\b",
            r"\bmon\s*reve\b",
            r"\bsarafin\b",
            r"\bsuvla\b",
            r"\bvinkara\b",
            r"\bchardonnay\b",
            r"\bsauvignon\b",
            r"\bnarince\b",
            r"\bşişe\b",
            r"\bsise\b",
            r"\b(70|75)\s*cl\b",
            r"\bepico\b",
            r"\bthia\b",
            r"\bkalecik\b",
            r"\bbornova\b",
            r"\bmisket\b",
            r"\bokuzgozu\b",
            r"\böküzgüzü\b",
            r"\bbogazkere\b",
            r"\bboğazkere\b",
            r"\bkabatepe\b",
        ],
    ),
    (
        "bira",
        [
            r"\bbira\b",
            r"\bbeer\b",
            r"\befes\b",
            r"\bbomonti\b",
            r"\bcorona\b",
            r"\bheineken\b",
            r"\btuborg\b",
            r"\bmiller\b",
            r"\bguinness\b",
            r"\bcarlsberg\b",
            r"\bbeck\b",
            r"\bfiltresiz\b",
            r"\bpint\b",
            r"\bdraft\b",
            r"\bclausthaler\b",
            r"\bbesi\s*bir\s*yerde\b",
        ],
    ),
    (
        "raki",
        [
            r"\brak[ıi]\b",
            r"\braki\b",
            r"\byeni\s*rak",
            r"\bbeylerbeyi\b",
            r"\btekirda[gğ]\b",
            r"\bsari\s*zeybek\b",
            r"\bsarı\s*zeybek\b",
            r"\bistanblue\b",
            r"\bistanbul\s*blue\b",
            r"\byeni\s*raki\b",
            r"\befe\s*gold\b",
        ],
    ),
    (
        "viski",
        [
            r"\bviski\b",
            r"\bwhisky\b",
            r"\bwhiskey\b",
            r"\bchivas\b",
            r"\bjameson\b",
            r"\bjack\s*daniel",
            r"\bballantine",
            r"\bglenlivet\b",
            r"\bdimple\b",
            r"\bjohnnie\b",
            r"\bmacallan\b",
            r"\bglenfiddich\b",
            r"\btalisker\b",
        ],
    ),
    (
        "votka",
        [r"\bvotka\b", r"\bvodka\b", r"\babsolut\b", r"\bsmirnoff\b", r"\bgrey\s*goose\b", r"\bbelvedere\b"],
    ),
    (
        "cin",
        [
            r"\bcin\b",
            r"\bgin\b",
            r"\bgordon",
            r"\bbeefeater\b",
            r"\bhendrick",
            r"\bbombay\b",
            r"\btonik\b",
            r"\btonic\b",
            r"\bg&t\b",
            r"\btanqueray\b",
            r"\bgilbey",
        ],
    ),
    (
        "tekila",
        [r"\btekila\b", r"\btequila\b", r"\bpatron\b", r"\bjose\s*cuervo\b", r"\bolmeca\b"],
    ),
    (
        "likor",
        [
            r"\blik[oö]r\b",
            r"\bliqueur\b",
            r"\bbaileys\b",
            r"\bjäger\b",
            r"\bjager\b",
            r"\bamaretto\b",
            r"\bjagermeister\b",
            r"\bcointreau\b",
            r"\barcher",
            r"\bkahlua\b",
            r"\bmalibu\b",
            r"\blimoncello\b",
            r"\bmartell\b",
            r"\bremy\s*martin\b",
            r"\bsafari\b",
            r"\bbrandy\b",
            r"\brum\b",
            r"\bcognac\b",
            r"\bhennessy\b",
        ],
    ),
    (
        "kahve",
        [
            r"\bkahve\b",
            r"\bcoffee\b",
            r"\bespresso\b",
            r"\blatte\b",
            r"\bcappuccino\b",
            r"\bamericano\b",
            r"\bmocha\b",
            r"\bflat\s*white\b",
            r"\btürk\s*kahvesi\b",
            r"\bturk\s*kahvesi\b",
            r"\bmacchiato\b",
            r"\bristretto\b",
            r"\blungo\b",
            r"\bfilter\b",
            r"\bfiltre\b",
            r"\bnescafe\b",
            r"\bdibek\b",
            r"\bmenengi[cç]\b",
        ],
    ),
    ("sicak_cikolata", [r"\bsıcak\s*çikolata\b", r"\bsicak\s*cikolata\b", r"\bhot\s*chocolate\b"]),
    ("salep", [r"\bsalep\b"]),
    ("siyah_cay", [r"\bçay\b", r"\bcay\b", r"\btea\b", r"\bearl\s*grey\b", r"\benglish\s*breakfast\b"]),
    ("yesil_cay", [r"\byeşil\s*çay\b", r"\byesil\s*cay\b", r"\bgreen\s*tea\b"]),
    (
        "bitki_cayi",
        [
            r"\bbitki\s*çay",
            r"\bbitki\s*cay",
            r"\bada\s*çayı\b",
            r"\badaçayı\b",
            r"\badacayi\b",
            r"\bpapatya\b",
            r"\bıhlamur\b",
            r"\bihlamur\b",
            r"\bkuşburnu\b",
            r"\bkusburnu\b",
        ],
    ),
    ("limonata", [r"\blimonata\b", r"\blemonade\b", r"\bserbet", r"\bşerbet", r"\bsherbet\b"]),
    ("ice_tea", [r"\bice\s*tea\b", r"\biced\s*tea\b", r"\bsoğuk\s*çay\b", r"\bsoguk\s*cay\b"]),
    ("ayran", [r"\bayran\b"]),
    (
        "gazli_icecek",
        [
            r"\bkola\b",
            r"\bcola\b",
            r"\bsoda\b",
            r"\bgazoz\b",
            r"\bsprite\b",
            r"\bfanta\b",
            r"\benerji\b",
            r"\bred\s*bull\b",
            r"\bpepsi\b",
            r"\b7up\b",
            r"\bschweppes\b",
            r"\bchurchill\b",
            r"\bcool\s*lime\b",
            r"\btonic\b",
            r"\bmaden\b",
            r"\bwater\b",
            r"\bsu\b",
        ],
    ),
    ("smoothie", [r"\bsmoothie\b", r"\bdetoks\b", r"\bdetox\b"]),
    ("milkshake", [r"\bmilkshake\b", r"\bshake\b"]),
    ("soguk_milkshake", [r"\bmilkshake\b", r"\bshake\b"]),
    ("kombucha", [r"\bkombucha\b"]),
    ("salgam", [r"\bşalgam\b", r"\bsalgam\b", r"\bturnip\b"]),
    ("sutlu_latte", [r"\blatte\b", r"\bflat\s*white\b"]),
    ("sutlu_sicak_cikolata", [r"\bsıcak\s*çikolata\b", r"\bsicak\s*cikolata\b", r"\bhot\s*chocolate\b"]),
    ("sutlu_milkshake", [r"\bmilkshake\b", r"\bshake\b"]),
    ("sutlu_cappuccino", [r"\bcappuccino\b"]),
    (
        "soguk_kahve",
        [
            r"\bice\s*latte\b",
            r"\bice\s*americano\b",
            r"\bice\s*caramel\b",
            r"\bice\s*filtre\b",
            r"\bice\s*white\b",
            r"\bice\s*mocha\b",
            r"\bsoguk\s*kahve\b",
        ],
    ),
    (
        "su_icecek",
        [
            r"\bsu\b",
            r"\bwater\b",
            r"\bpellegrino\b",
            r"\bmineral\s*water\b",
            r"\bmaden\s*suyu\b",
        ],
    ),
    (
        "taze_meyve_suyu",
        [
            r"\bsuyu\b",
            r"\bjuice\b",
            r"\bportakal\b",
            r"\belma\b",
            r"\bhavuç\b",
            r"\bhavuc\b",
            r"\bnar\b",
            r"\büzüm\b",
            r"\buzum\b",
            r"\bmeyve\s*suyu\b",
            r"\borange\s*juice\b",
            r"\bfruit\s*juice\b",
        ],
    ),
    (
        "soguk_meyve_suyu",
        [
            r"\bsuyu\b",
            r"\bjuice\b",
            r"\bmeyve\s*suyu\b",
            r"\borange\s*juice\b",
            r"\bfruit\s*juice\b",
        ],
    ),
    (
        "klasik_kokteyl",
        [
            r"\bmojito\b",
            r"\bnegroni\b",
            r"\bold\s*fashioned\b",
            r"\bmanhattan\b",
            r"\bwhisky\s*sour\b",
            r"\bgin\s*tonic\b",
            r"\bcosmopolitan\b",
            r"\bpiña\s*colada\b",
            r"\bpina\s*colada\b",
            r"\bmargarita\b",
            r"\bcaipirinha\b",
            r"\bdaiquiri\b",
            r"\bmartini\b",
            r"\bspritz\b",
            r"\baperol\b",
            r"\bbloody\s*mary\b",
            r"\bcuba\s*libre\b",
            r"\blong\s*island\b",
            r"\bsangria\b",
            r"\bsex\s*on\s*the\s*beach\b",
            r"\btom\s*collins\b",
            r"\bwhite\s*russian\b",
        ],
    ),
    ("imza_kokteyl", [r"\bimza\b", r"\bsignature\b", r"\bhouse\s*cocktail\b", r"\bözel\s*kokteyl\b", r"\bozel\s*kokteyl\b"]),
    ("mocktail", [r"\bmocktail\b", r"\bvirgin\b", r"\balkolsüz\s*kokteyl\b", r"\balkolsuz\s*kokteyl\b", r"\bfruit\s*punch\b"]),
]

SUB_DEFAULT_DESCRIPTOR: dict[str, str] = {
    "su": "su_icecek",
}


def fold_turkish(value: str) -> str:
    text = unicodedata.normalize("NFKD", value.casefold())
    return "".join(ch for ch in text if not unicodedata.combining(ch))


def load_seed_descriptors() -> list[dict[str, Any]]:
    document = json.loads(SEED_PATH.read_text(encoding="utf-8"))
    return list(document.get("descriptors") or [])


def load_sub_ids_by_slug(cur) -> dict[str, int]:
    cur.execute("SELECT id, slug FROM tbl_sub_category WHERE COALESCE(is_deleted, FALSE) = FALSE")
    return {row["slug"]: int(row["id"]) for row in cur.fetchall()}


def merge_descriptors(seed_descriptors: list[dict[str, Any]], sub_ids_by_slug: dict[str, int]) -> list[dict[str, Any]]:
    merged = {item["id"]: dict(item) for item in seed_descriptors}
    for spec in EXTRA_DESCRIPTOR_SPECS:
        sub_id = sub_ids_by_slug.get(spec["subSlug"])
        if sub_id is None:
            continue
        merged[spec["id"]] = {
            "id": spec["id"],
            "subCategoryId": sub_id,
            "slug": spec["slug"],
            "name": spec["name"],
            "sortOrder": spec.get("sortOrder", 0),
        }
    return list(merged.values())


def ensure_schema(cur) -> None:
    cur.execute("SELECT to_regclass('public.tbl_descriptor_category') AS name")
    if cur.fetchone()["name"] is not None:
        cur.execute(
            """
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'tbl_menu_products' AND column_name = 'descriptor_category_id'
            """
        )
        if cur.fetchone():
            return

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS tbl_descriptor_category (
            id                BIGINT PRIMARY KEY,
            sub_category_id   BIGINT NOT NULL,
            slug              VARCHAR(64) NOT NULL,
            name              VARCHAR(255) NOT NULL,
            sort_order        INTEGER NOT NULL DEFAULT 0,
            created_at        TIMESTAMP,
            updated_at        TIMESTAMP,
            is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
            CONSTRAINT uk_descriptor_category_slug UNIQUE (slug)
        )
        """
    )
    cur.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_descriptor_category_sub
            ON tbl_descriptor_category (sub_category_id)
        """
    )
    cur.execute(
        """
        ALTER TABLE tbl_menu_products
            ADD COLUMN IF NOT EXISTS descriptor_category_id BIGINT NULL
        """
    )
    cur.execute(
        """
        DO $$
        BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM pg_constraint WHERE conname = 'fk_menu_products_descriptor_category'
            ) THEN
                ALTER TABLE tbl_menu_products
                    ADD CONSTRAINT fk_menu_products_descriptor_category
                        FOREIGN KEY (descriptor_category_id) REFERENCES tbl_descriptor_category (id);
            END IF;
        END $$;
        """
    )


def upsert_descriptors(cur, descriptors: list[dict[str, Any]]) -> int:
    for item in descriptors:
        cur.execute(
            """
            INSERT INTO tbl_descriptor_category (
                id, sub_category_id, slug, name, sort_order, created_at, updated_at, is_deleted
            ) VALUES (%s, %s, %s, %s, %s, NOW(), NOW(), FALSE)
            ON CONFLICT (id) DO UPDATE SET
                sub_category_id = EXCLUDED.sub_category_id,
                slug = EXCLUDED.slug,
                name = EXCLUDED.name,
                sort_order = EXCLUDED.sort_order,
                updated_at = NOW(),
                is_deleted = FALSE
            """,
            (
                item["id"],
                item["subCategoryId"],
                item["slug"],
                item["name"],
                item.get("sortOrder", 0),
            ),
        )
    return len(descriptors)


def load_descriptor_maps(cur) -> tuple[dict[str, dict[str, int]], dict[int, set[str]]]:
    cur.execute(
        """
        SELECT id, sub_category_id, slug
        FROM tbl_descriptor_category
        WHERE COALESCE(is_deleted, FALSE) = FALSE
        """
    )
    by_sub_slug: dict[str, dict[str, int]] = {}
    allowed_by_sub: dict[int, set[str]] = {}
    for row in cur.fetchall():
        sub_id = int(row["sub_category_id"])
        slug = row["slug"]
        by_sub_slug.setdefault(str(sub_id), {})[slug] = int(row["id"])
        allowed_by_sub.setdefault(sub_id, set()).add(slug)
    return by_sub_slug, allowed_by_sub


def resolve_descriptor_slug(product_name: str, sub_slug: str, allowed: set[str]) -> str | None:
    folded_name = fold_turkish(product_name)
    for descriptor_slug, patterns in NAME_RULES:
        if descriptor_slug not in allowed:
            continue
        for pattern in patterns:
            if re.search(pattern, folded_name, flags=re.IGNORECASE):
                return descriptor_slug

    default_slug = SUB_DEFAULT_DESCRIPTOR.get(sub_slug)
    if default_slug and default_slug in allowed:
        return default_slug

    if len(allowed) == 1:
        return next(iter(allowed))

    return None


def assign_all_products(cur) -> dict[str, Any]:
    by_sub_slug, allowed_by_sub = load_descriptor_maps(cur)
    cur.execute(
        """
        SELECT p.product_id, p.name, p.sub_category_id, sc.slug AS sub_slug,
               p.descriptor_category_id, u.email
        FROM tbl_menu_products p
        JOIN tbl_menu m ON m.menu_id = p.menu_id
        JOIN tbl_user u ON u.id = m.user_id
        JOIN tbl_sub_category sc ON sc.id = p.sub_category_id
        WHERE COALESCE(p.is_deleted, FALSE) = FALSE
          AND COALESCE(m.is_deleted, FALSE) = FALSE
        ORDER BY u.email, p.product_id
        """
    )
    products = cur.fetchall()

    updated = 0
    unchanged = 0
    skipped_no_vocab = 0
    skipped_no_match = 0
    per_user: dict[str, dict[str, int]] = {}

    for product in products:
        email = product["email"]
        stats = per_user.setdefault(
            email,
            {"updated": 0, "unchanged": 0, "skipped_no_vocab": 0, "skipped_no_match": 0, "assigned_total": 0},
        )

        sub_id = int(product["sub_category_id"])
        allowed = allowed_by_sub.get(sub_id, set())
        if not allowed:
            skipped_no_vocab += 1
            stats["skipped_no_vocab"] += 1
            continue

        descriptor_slug = resolve_descriptor_slug(product["name"], product["sub_slug"], allowed)
        if descriptor_slug is None:
            skipped_no_match += 1
            stats["skipped_no_match"] += 1
            continue

        descriptor_id = by_sub_slug.get(str(sub_id), {}).get(descriptor_slug)
        if descriptor_id is None:
            skipped_no_match += 1
            stats["skipped_no_match"] += 1
            continue

        if product["descriptor_category_id"] == descriptor_id:
            unchanged += 1
            stats["unchanged"] += 1
            stats["assigned_total"] += 1
            continue

        cur.execute(
            """
            UPDATE tbl_menu_products
            SET descriptor_category_id = %s, updated_at = NOW()
            WHERE product_id = %s
            """,
            (descriptor_id, product["product_id"]),
        )
        updated += 1
        stats["updated"] += 1
        stats["assigned_total"] += 1

    return {
        "total_products": len(products),
        "updated": updated,
        "unchanged": unchanged,
        "skipped_no_vocab": skipped_no_vocab,
        "skipped_no_match": skipped_no_match,
        "assigned_total": updated + unchanged,
        "per_user": per_user,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", choices=("prod", "stage"), default="prod")
    args = parser.parse_args()
    env_label = args.env.upper()
    db_config = DB_BY_ENV[args.env]

    seed_descriptors = load_seed_descriptors()
    if not seed_descriptors:
        raise RuntimeError("No descriptors in menu-taxonomy.json")

    with psycopg2.connect(**db_config) as conn:
        conn.autocommit = False
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            ensure_schema(cur)
            sub_ids_by_slug = load_sub_ids_by_slug(cur)
            descriptors = merge_descriptors(seed_descriptors, sub_ids_by_slug)
            seeded = upsert_descriptors(cur, descriptors)
            result = assign_all_products(cur)
            conn.commit()

    print(f"{env_label} descriptors_upserted={seeded}")
    print(
        f"{env_label} totals: "
        f"products={result['total_products']} "
        f"assigned={result['assigned_total']} "
        f"updated={result['updated']} "
        f"unchanged={result['unchanged']} "
        f"skipped_no_vocab={result['skipped_no_vocab']} "
        f"skipped_no_match={result['skipped_no_match']}"
    )
    for email, stats in sorted(result["per_user"].items()):
        print(
            f"  {email}: assigned={stats['assigned_total']} "
            f"updated={stats['updated']} unchanged={stats['unchanged']} "
            f"no_vocab={stats['skipped_no_vocab']} no_match={stats['skipped_no_match']}"
        )


if __name__ == "__main__":
    main()
