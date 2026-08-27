#!/usr/bin/env python3
"""Upsert descriptor taxonomy and assign descriptor_category_id for trkhamarat@gmail.com menus."""

from __future__ import annotations

import json
import os
import re
import unicodedata
from pathlib import Path
from typing import Any

import psycopg2
import psycopg2.extras

USER_EMAIL = "trkhamarat@gmail.com"

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

SEED_PATH = Path(__file__).resolve().parents[1] / "src/main/resources/seed/menu-taxonomy.json"

EXTRA_DESCRIPTORS: list[dict[str, Any]] = [
    {"id": 1024, "subCategoryId": 6, "slug": "sutlu_latte", "name": "Latte", "sortOrder": 1},
    {"id": 1025, "subCategoryId": 6, "slug": "sutlu_sicak_cikolata", "name": "Sıcak Çikolata", "sortOrder": 2},
    {"id": 1026, "subCategoryId": 6, "slug": "sutlu_milkshake", "name": "Milkshake", "sortOrder": 3},
    {"id": 1027, "subCategoryId": 3, "slug": "taze_meyve_suyu", "name": "Taze Meyve Suyu", "sortOrder": 1},
    {"id": 1028, "subCategoryId": 2, "slug": "ice_tea", "name": "Ice Tea", "sortOrder": 4},
    {"id": 1029, "subCategoryId": 4, "slug": "salgam", "name": "Şalgam", "sortOrder": 2},
]

NAME_RULES: list[tuple[str, list[str]]] = [
    ("sarap", [r"\bşarap\b", r"\bsarap\b", r"\bwine\b", r"\bprosecco\b", r"\bchianti\b", r"\bmerlot\b", r"\bcabernet\b", r"\bshiraz\b", r"\bros[eé]\b", r"\bkadeh\b.*\b(kırmızı|beyaz|kirmizi)\b"]),
    ("bira", [r"\bbira\b", r"\bbeer\b", r"\be[f]?es\b", r"\bcorona\b", r"\bheineken\b", r"\btuborg\b", r"\bmiller\b", r"\bguinness\b", r"\bpint\b", r"\bdraft\b"]),
    ("raki", [r"\brak[ıi]\b", r"\braki\b", r"\byeni\s*rak[ıi]\b"]),
    ("viski", [r"\bviski\b", r"\bwhisky\b", r"\bwhiskey\b", r"\bjack\b", r"\bj\b\s*d\b", r"\bchivas\b", r"\bjameson\b"]),
    ("votka", [r"\bvotka\b", r"\bvodka\b", r"\bsmirnoff\b", r"\babsolut\b"]),
    ("cin", [r"\bcin\b", r"\bgin\b", r"\btonik\b", r"\btonic\b", r"\bg&t\b"]),
    ("tekila", [r"\btekila\b", r"\btequila\b", r"\bmargarita\b"]),
    ("likor", [r"\blik[oö]r\b", r"\bliqueur\b", r"\bbaileys\b", r"\bjäger\b", r"\bjager\b", r"\bamaretto\b"]),
    ("kahve", [r"\bkahve\b", r"\bcoffee\b", r"\bespresso\b", r"\blatte\b", r"\bcappuccino\b", r"\bamericano\b", r"\bmocha\b", r"\bflat\s*white\b", r"\btürk\s*kahvesi\b", r"\bturk\s*kahvesi\b"]),
    ("sicak_cikolata", [r"\bsıcak\s*çikolata\b", r"\bsicak\s*cikolata\b", r"\bhot\s*chocolate\b"]),
    ("salep", [r"\bsalep\b"]),
    ("siyah_cay", [r"\bçay\b", r"\bcay\b", r"\btea\b", r"\bearl\s*grey\b", r"\benglish\s*breakfast\b"]),
    ("yesil_cay", [r"\byeşil\s*çay\b", r"\byesil\s*cay\b", r"\bgreen\s*tea\b"]),
    ("bitki_cayi", [r"\bbitki\s*çayı\b", r"\bbitki\s*cayi\b", r"\bada\s*çayı\b", r"\badaçayı\b", r"\badacayi\b", r"\bpapatya\b", r"\bıhlamur\b", r"\bihlamur\b"]),
    ("limonata", [r"\blimonata\b", r"\blemonade\b"]),
    ("ice_tea", [r"\bice\s*tea\b", r"\biced\s*tea\b", r"\bsoğuk\s*çay\b", r"\bsoguk\s*cay\b"]),
    ("ayran", [r"\bayran\b"]),
    ("gazli_icecek", [r"\bkola\b", r"\bcola\b", r"\bsoda\b", r"\bgazoz\b", r"\bsprite\b", r"\bfanta\b", r"\btonic\b", r"\benerji\b", r"\bred\s*bull\b"]),
    ("smoothie", [r"\bsmoothie\b", r"\bdetoks\b", r"\bdetox\b"]),
    ("milkshake", [r"\bmilkshake\b", r"\bshake\b"]),
    ("kombucha", [r"\bkombucha\b"]),
    ("salgam", [r"\bşalgam\b", r"\bsalgam\b", r"\bturnip\b"]),
    ("sutlu_latte", [r"\blatte\b", r"\bflat\s*white\b"]),
    ("sutlu_sicak_cikolata", [r"\bsıcak\s*çikolata\b", r"\bsicak\s*cikolata\b", r"\bhot\s*chocolate\b"]),
    ("sutlu_milkshake", [r"\bmilkshake\b", r"\bshake\b"]),
    ("taze_meyve_suyu", [r"\bsuyu\b", r"\bjuice\b", r"\bportakal\b", r"\belma\b", r"\bhavuç\b", r"\bhavuc\b", r"\bnar\b", r"\büzüm\b", r"\buzum\b"]),
    ("klasik_kokteyl", [r"\bmojito\b", r"\bnegroni\b", r"\bold\s*fashioned\b", r"\bmanhattan\b", r"\bwhisky\s*sour\b", r"\bgin\s*tonic\b", r"\bcosmopolitan\b", r"\bpiña\s*colada\b", r"\bpina\s*colada\b", r"\bmargarita\b"]),
    ("imza_kokteyl", [r"\bimza\b", r"\bsignature\b", r"\bhouse\s*cocktail\b", r"\bözel\s*kokteyl\b", r"\bozel\s*kokteyl\b"]),
    ("mocktail", [r"\bmocktail\b", r"\bvirgin\b", r"\balkolsüz\s*kokteyl\b", r"\balkolsuz\s*kokteyl\b"]),
]

SUB_DEFAULT_DESCRIPTOR: dict[str, str] = {
    "su": "gazli_icecek",
}


def fold_turkish(value: str) -> str:
    text = unicodedata.normalize("NFKD", value.casefold())
    return "".join(ch for ch in text if not unicodedata.combining(ch))


def load_descriptors() -> list[dict[str, Any]]:
    document = json.loads(SEED_PATH.read_text(encoding="utf-8"))
    descriptors = list(document.get("descriptors") or [])
    known_ids = {item["id"] for item in descriptors}
    for extra in EXTRA_DESCRIPTORS:
        if extra["id"] not in known_ids:
            descriptors.append(extra)
    return descriptors


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
    count = 0
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
        count += 1
    return count


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


def assign_for_user(cur, user_id: int) -> tuple[int, int, int]:
    by_sub_slug, allowed_by_sub = load_descriptor_maps(cur)
    cur.execute(
        """
        SELECT p.product_id, p.name, p.sub_category_id, sc.slug AS sub_slug, p.descriptor_category_id
        FROM tbl_menu_products p
        JOIN tbl_menu m ON m.menu_id = p.menu_id
        JOIN tbl_sub_category sc ON sc.id = p.sub_category_id
        WHERE m.user_id = %s
          AND COALESCE(p.is_deleted, FALSE) = FALSE
          AND COALESCE(m.is_deleted, FALSE) = FALSE
        ORDER BY p.product_id
        """,
        (user_id,),
    )
    products = cur.fetchall()

    updated = 0
    skipped_no_descriptor = 0
    unchanged = 0

    for product in products:
        sub_id = int(product["sub_category_id"])
        allowed = allowed_by_sub.get(sub_id, set())
        if not allowed:
            skipped_no_descriptor += 1
            continue

        descriptor_slug = resolve_descriptor_slug(product["name"], product["sub_slug"], allowed)
        if descriptor_slug is None:
            skipped_no_descriptor += 1
            continue

        descriptor_id = by_sub_slug.get(str(sub_id), {}).get(descriptor_slug)
        if descriptor_id is None:
            skipped_no_descriptor += 1
            continue

        cur.execute(
            """
            UPDATE tbl_menu_products
            SET descriptor_category_id = %s, updated_at = NOW()
            WHERE product_id = %s
            """,
            (descriptor_id, product["product_id"]),
        )
        if product["descriptor_category_id"] == descriptor_id:
            unchanged += 1
        else:
            updated += 1

    return updated, unchanged, skipped_no_descriptor


def run_env(label: str, cfg: dict[str, Any], descriptors: list[dict[str, Any]]) -> None:
    with psycopg2.connect(**cfg) as conn:
        conn.autocommit = False
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            ensure_schema(cur)
            seeded = upsert_descriptors(cur, descriptors)
            cur.execute("SELECT id, email FROM tbl_user WHERE email = %s", (USER_EMAIL,))
            user = cur.fetchone()
            if not user:
                raise RuntimeError(f"{label}: user not found: {USER_EMAIL}")

            updated, unchanged, skipped = assign_for_user(cur, int(user["id"]))
            conn.commit()

    print(
        f"{label}: descriptors_upserted={seeded} updated={updated} unchanged={unchanged} "
        f"skipped_no_descriptor_vocab={skipped} user={USER_EMAIL}"
    )


def main() -> None:
    descriptors = load_descriptors()
    if not descriptors:
        raise RuntimeError("No descriptors in menu-taxonomy.json")

    run_env("PROD", PROD, descriptors)
    run_env("STAGE", STAGE, descriptors)


if __name__ == "__main__":
    main()
