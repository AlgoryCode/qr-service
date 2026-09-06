#!/usr/bin/env python3
from __future__ import annotations

import json
from datetime import datetime, timezone

import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)

USER_ID = 22
USER_EMAIL = "trkhamarat@gmail.com"
BRANCH_NAME = "Tarihi Ekmek İçi Sokak Köftecisi"
BUSINESS_NAME = "Tarihi Ekmek İçi Sokak Köftecisi"
THEME_ID = "modern-bistro"
PURCHASE_ID = 333

CATEGORIES: list[tuple[str, str, list[tuple[str, str, list[tuple[str, str | None, float]]]]]] = [
    (
        "ekmek_arasi",
        "Ekmek Arası",
        [
            (
                "tavuk",
                "Tavuk",
                [
                    ("Yarım Tavuk", "90 gr", 150),
                    ("3 Çeyrek Tavuk", "140 gr", 240),
                    ("Tam Ekmek Tavuk", "180 gr", 360),
                ],
            ),
            (
                "sucuk",
                "Sucuk",
                [
                    ("Yarım Sucuk", "90 gr", 250),
                    ("3 Çeyrek Sucuk", "140 gr", 380),
                    ("Tam Ekmek Sucuk", "180 gr", 500),
                ],
            ),
            (
                "kofte",
                "Köfte",
                [
                    ("Yarım Köfte", "90 gr", 270),
                    ("3 Çeyrek Köfte", "140 gr", 400),
                    ("Tam Ekmek Köfte", "180 gr", 540),
                ],
            ),
        ],
    ),
    (
        "porsiyonlar",
        "Porsiyonlar",
        [
            (
                "porsiyonlar",
                "Porsiyonlar",
                [
                    ("Porsiyon Tavuk", "190 gr", 350),
                    ("Porsiyon Sucuk", "190 gr", 500),
                    ("Köfte Porsiyon", "190 gr", 550),
                    ("2'li Karışık Porsiyon", "210 gr", 550),
                    ("3'lü Karışık Porsiyon", "230 gr", 570),
                ],
            ),
        ],
    ),
    (
        "icecekler",
        "İçecekler",
        [
            (
                "icecekler",
                "İçecekler",
                [
                    ("Su", None, 20),
                    ("7UP", "330 ml", 70),
                    ("Yedigün", "330 ml", 70),
                    ("Kola", "330 ml", 70),
                    ("Cam Şişe Ayran", None, 50),
                    ("Küçük Ayran", None, 40),
                    ("Soda", "250 ml", 40),
                    ("Tropikana", None, 70),
                    ("Şalgam", None, 60),
                    ("Ice Tea", "330 ml", 70),
                ],
            ),
        ],
    ),
]


def now_utc() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def next_id(cur, table: str) -> int:
    cur.execute(f"SELECT COALESCE(MAX(id), 0) + 1 FROM {table}")
    return int(cur.fetchone()[0])


def sync_seq(cur, seq: str, table: str, id_col: str = "id") -> None:
    cur.execute(
        f"SELECT setval(%s, (SELECT COALESCE(MAX({id_col}), 1) FROM {table}))",
        (seq,),
    )


def ensure_user(cur) -> None:
    cur.execute("SELECT id, email FROM tbl_user WHERE id = %s", (USER_ID,))
    row = cur.fetchone()
    if not row or row[1] != USER_EMAIL:
        raise RuntimeError(f"Expected {USER_EMAIL} id={USER_ID}, found {row}")


def find_existing_branch(cur) -> int | None:
    cur.execute(
        """
        SELECT id FROM tbl_branch
        WHERE user_id = %s AND is_deleted = false AND name = %s
        ORDER BY id DESC LIMIT 1
        """,
        (USER_ID, BRANCH_NAME),
    )
    row = cur.fetchone()
    return row[0] if row else None


def create_branch(cur) -> int:
    existing = find_existing_branch(cur)
    if existing:
        return existing
    now = now_utc()
    cur.execute(
        """
        INSERT INTO tbl_branch (
            created_at, is_deleted, updated_at, active, address, email,
            grandfathered, name, phone, photo_key, photo_url, user_id
        ) VALUES (
            %s, false, %s, true, NULL, %s,
            false, %s, NULL, NULL, NULL, %s
        )
        RETURNING id
        """,
        (now, now, USER_EMAIL, BRANCH_NAME, USER_ID),
    )
    return int(cur.fetchone()[0])


def find_existing_menu(cur, branch_id: int) -> tuple[int, int] | None:
    cur.execute(
        """
        SELECT menu_id, qr_id FROM tbl_menu
        WHERE user_id = %s AND branch_id = %s AND is_deleted = false
          AND business_name = %s
        ORDER BY menu_id DESC LIMIT 1
        """,
        (USER_ID, branch_id, BUSINESS_NAME),
    )
    row = cur.fetchone()
    if not row:
        return None
    return int(row[0]), int(row[1])


def create_qr_and_menu(cur, branch_id: int) -> tuple[int, int]:
    existing = find_existing_menu(cur, branch_id)
    if existing:
        return existing

    now = now_utc()
    details = {
        "email": USER_EMAIL,
        "themeId": THEME_ID,
        "branchId": branch_id,
        "businessName": BUSINESS_NAME,
    }
    cur.execute(
        """
        INSERT INTO tbl_qr (
            created_at, is_deleted, updated_at, details, img_src, qr_name,
            qr_type_id, customer_id, user_id, purchase_id, active
        ) VALUES (
            %s, false, %s, %s::jsonb, NULL, %s,
            NULL, NULL, %s, %s, true
        )
        RETURNING qr_id
        """,
        (now, now, json.dumps(details, ensure_ascii=False), BUSINESS_NAME, USER_ID, PURCHASE_ID),
    )
    qr_id = int(cur.fetchone()[0])
    public_url = f"https://stage.algorycode.com/menu/{qr_id}"
    details["publicUrl"] = public_url
    cur.execute(
        """
        UPDATE tbl_qr
        SET details = %s::jsonb, updated_at = %s
        WHERE qr_id = %s
        """,
        (json.dumps(details, ensure_ascii=False), now, qr_id),
    )

    cur.execute(
        """
        INSERT INTO tbl_menu (
            created_at, is_deleted, updated_at, active, address, business_name,
            email, package_id, phone, qr_id, theme_id, user_id, chef_avatar_key,
            chef_name, logo_key, logo_url, public_access_disabled_reason,
            public_access_enabled, rating_avg, rating_count, slogan, branch_id
        ) VALUES (
            %s, false, %s, true, NULL, %s,
            %s, NULL, NULL, %s, %s, %s, 'default',
            NULL, NULL, NULL, NULL,
            true, 0, 0, NULL, %s
        )
        RETURNING menu_id
        """,
        (now, now, BUSINESS_NAME, USER_EMAIL, qr_id, THEME_ID, USER_ID, branch_id),
    )
    menu_id = int(cur.fetchone()[0])
    return menu_id, qr_id


def upsert_main(cur, menu_id: int, slug: str, name: str, sort_order: int) -> int:
    now = now_utc()
    cur.execute(
        """
        SELECT id FROM tbl_menu_category
        WHERE menu_id = %s AND slug = %s
        ORDER BY is_deleted ASC, id ASC LIMIT 1
        """,
        (menu_id, slug),
    )
    row = cur.fetchone()
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET is_deleted = false, name = %s, sort_order = %s, updated_at = %s, user_id = %s
            WHERE id = %s
            """,
            (name, sort_order, now, USER_ID, row[0]),
        )
        return int(row[0])
    new_id = next_id(cur, "tbl_menu_category")
    cur.execute(
        """
        INSERT INTO tbl_menu_category (
            id, menu_id, user_id, slug, name, sort_order, created_at, updated_at, is_deleted
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, false)
        """,
        (new_id, menu_id, USER_ID, slug, name, sort_order, now, now),
    )
    return new_id


def upsert_sub(cur, menu_id: int, main_id: int, slug: str, name: str, sort_order: int) -> int:
    now = now_utc()
    cur.execute(
        """
        SELECT id FROM tbl_menu_sub_category
        WHERE menu_id = %s AND slug = %s
        ORDER BY is_deleted ASC, id ASC LIMIT 1
        """,
        (menu_id, slug),
    )
    row = cur.fetchone()
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET is_deleted = false, menu_category_id = %s, name = %s,
                sort_order = %s, updated_at = %s
            WHERE id = %s
            """,
            (main_id, name, sort_order, now, row[0]),
        )
        return int(row[0])
    new_id = next_id(cur, "tbl_menu_sub_category")
    cur.execute(
        """
        INSERT INTO tbl_menu_sub_category (
            id, menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, false)
        """,
        (new_id, menu_id, main_id, slug, name, sort_order, now, now),
    )
    return new_id


def upsert_product(
    cur,
    menu_id: int,
    sub_id: int,
    name: str,
    description: str | None,
    price: float,
    sort_order: int,
) -> int:
    now = now_utc()
    cur.execute(
        """
        SELECT product_id FROM tbl_menu_products
        WHERE menu_id = %s AND name = %s AND is_deleted = false
        ORDER BY product_id ASC LIMIT 1
        """,
        (menu_id, name),
    )
    row = cur.fetchone()
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_products
            SET sub_category_id = %s, description = %s, price = %s, currency = 'TRY',
                sort_order = %s, available = true, updated_at = %s
            WHERE product_id = %s
            """,
            (sub_id, description, price, sort_order, now, row[0]),
        )
        return int(row[0])
    cur.execute(
        """
        INSERT INTO tbl_menu_products (
            created_at, is_deleted, updated_at, available, category, currency,
            description, image_url, menu_id, name, price, sort_order,
            chef_recommended, nutrition, rating_avg, rating_count,
            serves_people_max, serves_people_min, sub_category_id
        ) VALUES (
            %s, false, %s, true, NULL, 'TRY',
            %s, NULL, %s, %s, %s, %s,
            false, '{}'::jsonb, 0, 0,
            NULL, NULL, %s
        )
        RETURNING product_id
        """,
        (now, now, description, menu_id, name, price, sort_order, sub_id),
    )
    return int(cur.fetchone()[0])


def seed_catalog(cur, menu_id: int) -> tuple[int, int, int]:
    sync_seq(cur, "tbl_menu_products_product_id_seq", "tbl_menu_products", "product_id")
    product_count = 0
    category_count = 0
    sub_count = 0
    for main_order, (main_slug, main_name, subs) in enumerate(CATEGORIES):
        main_id = upsert_main(cur, menu_id, main_slug, main_name, main_order)
        category_count += 1
        for sub_order, (sub_slug, sub_name, products) in enumerate(subs):
            unique_sub_slug = sub_slug if main_slug == sub_slug else f"{main_slug}_{sub_slug}"
            sub_id = upsert_sub(cur, menu_id, main_id, unique_sub_slug, sub_name, sub_order)
            sub_count += 1
            for prod_order, (name, detail, price) in enumerate(products):
                upsert_product(cur, menu_id, sub_id, name, detail, price, prod_order * 10)
                product_count += 1
    sync_seq(cur, "tbl_menu_category_id_seq", "tbl_menu_category")
    sync_seq(cur, "tbl_menu_sub_category_id_seq", "tbl_menu_sub_category")
    sync_seq(cur, "tbl_menu_products_product_id_seq", "tbl_menu_products", "product_id")
    return category_count, sub_count, product_count


def print_summary(cur, branch_id: int, menu_id: int, qr_id: int) -> None:
    print(f"branch_id={branch_id}")
    print(f"menu_id={menu_id}")
    print(f"qr_id={qr_id}")
    print(f"public=https://stage.algorycode.com/menu/{qr_id}")
    cur.execute(
        """
        SELECT mc.sort_order, mc.name, sc.name, p.name, p.price, p.description
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND p.is_deleted = false
        ORDER BY mc.sort_order, sc.sort_order, p.sort_order, p.product_id
        """,
        (menu_id,),
    )
    for row in cur.fetchall():
        print(f"  [{row[0]}] {row[1]} / {row[2]} | {row[3]} | {row[4]} | {row[5]}")


def main() -> None:
    conn = psycopg2.connect(**STAGE)
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            ensure_user(cur)
            branch_id = create_branch(cur)
            menu_id, qr_id = create_qr_and_menu(cur, branch_id)
            cats, subs, products = seed_catalog(cur, menu_id)
            conn.commit()
            print(f"OK {USER_EMAIL}: categories={cats} subs={subs} products={products}")
            print_summary(cur, branch_id, menu_id, qr_id)
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
