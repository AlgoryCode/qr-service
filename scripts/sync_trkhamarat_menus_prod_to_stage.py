#!/usr/bin/env python3
"""Sync trkhamarat@gmail.com active prod menus (catalog + public_id) onto stage."""

from __future__ import annotations

import os
from datetime import datetime, timezone

import psycopg2
from psycopg2.extras import Json

EMAIL = "trkhamarat@gmail.com"
PROD_MENU_IDS = (8, 14)
STAGE_MENU_BY_PROD = {8: 8, 14: None}

STAGE = dict(
    host=os.environ.get("STAGE_DB_HOST", "185.184.210.52"),
    port=int(os.environ.get("STAGE_DB_PORT", "5433")),
    dbname=os.environ.get("STAGE_DB_NAME", "algoryqrdb-stage"),
    user=os.environ.get("STAGE_DB_USER", "postgres"),
    password=os.environ.get("STAGE_DB_PASSWORD", "postgres_stage"),
    sslmode="disable",
)
PROD = dict(
    host=os.environ.get("PROD_DB_HOST", "185.184.210.52"),
    port=int(os.environ.get("PROD_DB_PORT", "5432")),
    dbname=os.environ.get("PROD_DB_NAME", "algoryqrdb"),
    user=os.environ.get("PROD_DB_USER", "postgres"),
    password=os.environ.get("PROD_DB_PASSWORD", ""),
    sslmode="disable",
)

SKIP_MENU_COLUMNS = frozenset(
    {
        "menu_id",
        "qr_id",
        "user_id",
        "branch_id",
        "created_at",
        "updated_at",
        "active",
        "is_deleted",
        "public_access_enabled",
        "public_access_disabled_reason",
    }
)


def fetch_one_dict(cur, query: str, params: tuple = ()) -> dict | None:
    cur.execute(query, params)
    row = cur.fetchone()
    if row is None:
        return None
    columns = [desc[0] for desc in cur.description]
    return dict(zip(columns, row, strict=True))


def table_columns(cur, table_name: str) -> set[str]:
    cur.execute(
        """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = %s
        """,
        (table_name,),
    )
    return {row[0] for row in cur.fetchall()}


def insert_row(cur, table_name: str, row: dict[str, object], allowed_columns: set[str]) -> None:
    payload = {key: value for key, value in row.items() if key in allowed_columns}
    values = []
    for column in payload:
        value = payload[column]
        if isinstance(value, (dict, list)):
            values.append(Json(value))
        else:
            values.append(value)
    columns = list(payload.keys())
    placeholders = ", ".join(["%s"] * len(columns))
    column_sql = ", ".join(columns)
    cur.execute(f"INSERT INTO {table_name} ({column_sql}) VALUES ({placeholders})", values)


def resolve_stage_user_id(stage_cur, prod_cur) -> int:
    stage_user = fetch_one_dict(stage_cur, "SELECT id FROM tbl_user WHERE email = %s", (EMAIL,))
    if stage_user:
        return stage_user["id"]
    prod_user = fetch_one_dict(prod_cur, "SELECT * FROM tbl_user WHERE email = %s", (EMAIL,))
    if prod_user is None:
        raise RuntimeError(f"User not found: {EMAIL}")
    stage_cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_user")
    new_user_id = stage_cur.fetchone()[0]
    stage_user_columns = table_columns(stage_cur, "tbl_user")
    payload = {key: prod_user[key] for key in prod_user if key != "id" and key in stage_user_columns}
    payload["id"] = new_user_id
    insert_row(stage_cur, "tbl_user", payload, stage_user_columns)
    return new_user_id


def find_stage_menu_by_public_id(stage_cur, public_id: str) -> int | None:
    row = fetch_one_dict(
        stage_cur,
        "SELECT menu_id FROM tbl_menu WHERE public_id = %s AND is_deleted = false",
        (public_id,),
    )
    return row["menu_id"] if row else None


def ensure_stage_menu_id(stage_cur, prod_cur, prod_menu_id: int, stage_user_id: int) -> int:
    mapped = STAGE_MENU_BY_PROD.get(prod_menu_id)
    if mapped is not None:
        return mapped

    prod_menu = fetch_one_dict(prod_cur, "SELECT * FROM tbl_menu WHERE menu_id = %s", (prod_menu_id,))
    if prod_menu is None:
        raise RuntimeError(f"Prod menu {prod_menu_id} not found")

    existing = find_stage_menu_by_public_id(stage_cur, prod_menu["public_id"])
    if existing is not None:
        return existing

    stage_cur.execute("SELECT COALESCE(MAX(menu_id), 0) + 1 FROM tbl_menu")
    new_menu_id = stage_cur.fetchone()[0]
    stage_cur.execute("SELECT COALESCE(MAX(qr_id), 0) + 1 FROM tbl_qr")
    new_qr_id = stage_cur.fetchone()[0]

    stage_cur.execute(
        """
        SELECT id FROM tbl_purchase
        WHERE user_id = %s AND status = 'ACTIVE'
        ORDER BY id DESC
        LIMIT 1
        """,
        (stage_user_id,),
    )
    purchase_row = stage_cur.fetchone()
    purchase_id = purchase_row[0] if purchase_row else None

    prod_qr = None
    if prod_menu.get("qr_id"):
        prod_qr = fetch_one_dict(prod_cur, "SELECT * FROM tbl_qr WHERE qr_id = %s", (prod_menu["qr_id"],))

    stage_cur.execute(
        """
        INSERT INTO tbl_qr (
          qr_id, user_id, qr_name, details, img_src, purchase_id, active, is_deleted, created_at, updated_at
        )
        VALUES (%s, %s, %s, %s, %s, %s, TRUE, FALSE, NOW(), NOW())
        """,
        (
            new_qr_id,
            stage_user_id,
            (prod_qr or {}).get("qr_name") or prod_menu.get("business_name") or f"Menu {prod_menu_id}",
            Json((prod_qr or {}).get("details") or {}),
            (prod_qr or {}).get("img_src"),
            purchase_id,
        ),
    )

    menu_columns = table_columns(stage_cur, "tbl_menu")
    payload = {key: prod_menu[key] for key in prod_menu if key in menu_columns}
    payload["menu_id"] = new_menu_id
    payload["qr_id"] = new_qr_id
    payload["user_id"] = stage_user_id
    payload["branch_id"] = None
    payload["active"] = True
    payload["is_deleted"] = False
    payload["public_access_enabled"] = True
    payload["created_at"] = datetime.now(timezone.utc)
    payload["updated_at"] = datetime.now(timezone.utc)
    insert_row(stage_cur, "tbl_menu", payload, menu_columns)
    return new_menu_id


def ensure_stage_menu_qr(
    stage_cur,
    prod_cur,
    prod_menu_id: int,
    stage_menu_id: int,
    stage_user_id: int,
) -> None:
    stage_menu = fetch_one_dict(stage_cur, "SELECT qr_id FROM tbl_menu WHERE menu_id = %s", (stage_menu_id,))
    if stage_menu and stage_menu.get("qr_id"):
        return

    prod_menu = fetch_one_dict(prod_cur, "SELECT qr_id FROM tbl_menu WHERE menu_id = %s", (prod_menu_id,))
    prod_qr_id = prod_menu.get("qr_id") if prod_menu else None
    prod_qr = (
        fetch_one_dict(prod_cur, "SELECT * FROM tbl_qr WHERE qr_id = %s", (prod_qr_id,))
        if prod_qr_id
        else None
    )
    qr_name = (prod_qr or {}).get("qr_name") or "Menu QR"

    stage_cur.execute(
        """
        SELECT qr_id FROM tbl_qr
        WHERE user_id = %s AND is_deleted = false
          AND qr_id NOT IN (
            SELECT qr_id FROM tbl_menu WHERE qr_id IS NOT NULL AND is_deleted = false
          )
        ORDER BY qr_id
        LIMIT 1
        """,
        (stage_user_id,),
    )
    free_qr = stage_cur.fetchone()
    if free_qr:
        stage_qr_id = free_qr[0]
        stage_cur.execute(
            "UPDATE tbl_qr SET qr_name = %s, active = TRUE, is_deleted = FALSE, updated_at = NOW() WHERE qr_id = %s",
            (qr_name, stage_qr_id),
        )
    else:
        stage_cur.execute("SELECT COALESCE(MAX(qr_id), 0) + 1 FROM tbl_qr")
        stage_qr_id = stage_cur.fetchone()[0]
        stage_cur.execute(
            """
            SELECT id FROM tbl_purchase
            WHERE user_id = %s AND status = 'ACTIVE'
            ORDER BY id DESC
            LIMIT 1
            """,
            (stage_user_id,),
        )
        purchase_row = stage_cur.fetchone()
        purchase_id = purchase_row[0] if purchase_row else None
        stage_cur.execute(
            """
            INSERT INTO tbl_qr (
              qr_id, user_id, qr_name, details, img_src, purchase_id, active, is_deleted, created_at, updated_at
            )
            VALUES (%s, %s, %s, '{}'::jsonb, NULL, %s, TRUE, FALSE, NOW(), NOW())
            """,
            (stage_qr_id, stage_user_id, qr_name, purchase_id),
        )

    stage_cur.execute(
        "UPDATE tbl_menu SET qr_id = %s, updated_at = NOW() WHERE menu_id = %s",
        (stage_qr_id, stage_menu_id),
    )


def apply_menu_profile(stage_cur, prod_cur, prod_menu_id: int, stage_menu_id: int, stage_user_id: int) -> None:
    prod_menu = fetch_one_dict(prod_cur, "SELECT * FROM tbl_menu WHERE menu_id = %s", (prod_menu_id,))
    if prod_menu is None:
        raise RuntimeError(f"Prod menu {prod_menu_id} missing")

    ensure_stage_menu_qr(stage_cur, prod_cur, prod_menu_id, stage_menu_id, stage_user_id)

    stage_columns = table_columns(stage_cur, "tbl_menu")
    assignments = []
    values: list[object] = []
    for key, value in prod_menu.items():
        if key in SKIP_MENU_COLUMNS or key not in stage_columns:
            continue
        assignments.append(f"{key} = %s")
        values.append(value)
    assignments.extend(
        [
            "active = TRUE",
            "is_deleted = FALSE",
            "public_access_enabled = TRUE",
            "public_access_disabled_reason = NULL",
            "user_id = %s",
            "updated_at = NOW()",
        ]
    )
    values.append(stage_user_id)
    values.append(stage_menu_id)
    stage_cur.execute(
        f"UPDATE tbl_menu SET {', '.join(assignments)} WHERE menu_id = %s",
        values,
    )


def clear_stage_catalog(stage_cur, stage_menu_id: int) -> None:
    stage_cur.execute("SELECT product_id FROM tbl_menu_products WHERE menu_id = %s", (stage_menu_id,))
    product_ids = [row[0] for row in stage_cur.fetchall()]
    if product_ids:
        stage_cur.execute(
            "DELETE FROM tbl_menu_product_allergen WHERE product_id = ANY(%s)",
            (product_ids,),
        )
        stage_cur.execute(
            "DELETE FROM tbl_menu_product_tag WHERE product_id = ANY(%s)",
            (product_ids,),
        )
        stage_cur.execute(
            "DELETE FROM tbl_menu_product_pairing WHERE product_id = ANY(%s) OR target_product_id = ANY(%s)",
            (product_ids, product_ids),
        )
        stage_cur.execute(
            "DELETE FROM tbl_menu_product_option_group WHERE product_id = ANY(%s)",
            (product_ids,),
        )
        stage_cur.execute(
            "DELETE FROM tbl_stock_recipe_line WHERE product_id = ANY(%s)",
            (product_ids,),
        )
    stage_cur.execute("DELETE FROM tbl_menu_products WHERE menu_id = %s", (stage_menu_id,))
    stage_cur.execute("DELETE FROM tbl_menu_sub_category WHERE menu_id = %s", (stage_menu_id,))
    stage_cur.execute("DELETE FROM tbl_menu_category WHERE menu_id = %s", (stage_menu_id,))


def clone_taxonomy(stage_cur, prod_cur, prod_menu_id: int, stage_menu_id: int, stage_user_id: int) -> dict[int, int]:
    prod_cur.execute(
        """
        SELECT *
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (prod_menu_id,),
    )
    prod_category_columns = [desc[0] for desc in prod_cur.description]
    stage_category_columns = table_columns(stage_cur, "tbl_menu_category")
    category_map: dict[int, int] = {}

    for row in prod_cur.fetchall():
        source = dict(zip(prod_category_columns, row, strict=True))
        old_id = source["id"]
        stage_cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_category")
        new_id = stage_cur.fetchone()[0]
        source["id"] = new_id
        source["menu_id"] = stage_menu_id
        source["user_id"] = stage_user_id
        source["created_at"] = datetime.now(timezone.utc)
        source["updated_at"] = datetime.now(timezone.utc)
        insert_row(stage_cur, "tbl_menu_category", source, stage_category_columns)
        category_map[old_id] = new_id

    prod_cur.execute(
        """
        SELECT *
        FROM tbl_menu_sub_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY menu_category_id, sort_order, id
        """,
        (prod_menu_id,),
    )
    prod_sub_columns = [desc[0] for desc in prod_cur.description]
    stage_sub_columns = table_columns(stage_cur, "tbl_menu_sub_category")
    sub_map: dict[int, int] = {}

    for row in prod_cur.fetchall():
        source = dict(zip(prod_sub_columns, row, strict=True))
        parent_category_id = source.get("menu_category_id")
        if parent_category_id not in category_map:
            continue
        old_id = source["id"]
        stage_cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_sub_category")
        new_id = stage_cur.fetchone()[0]
        source["id"] = new_id
        source["menu_id"] = stage_menu_id
        source["menu_category_id"] = category_map[source["menu_category_id"]]
        source["created_at"] = datetime.now(timezone.utc)
        source["updated_at"] = datetime.now(timezone.utc)
        insert_row(stage_cur, "tbl_menu_sub_category", source, stage_sub_columns)
        sub_map[old_id] = new_id

    return sub_map


def clone_products(
    stage_cur,
    prod_cur,
    prod_menu_id: int,
    stage_menu_id: int,
    stage_user_id: int,
    sub_map: dict[int, int],
) -> int:
    prod_cur.execute(
        """
        SELECT *
        FROM tbl_menu_products
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY product_id
        """,
        (prod_menu_id,),
    )
    prod_product_columns = [desc[0] for desc in prod_cur.description]
    stage_product_columns = table_columns(stage_cur, "tbl_menu_products")
    count = 0

    for row in prod_cur.fetchall():
        source = dict(zip(prod_product_columns, row, strict=True))
        if source.get("sub_category_id") is not None:
            mapped_sub = sub_map.get(source["sub_category_id"])
            if mapped_sub is None:
                continue
            source["sub_category_id"] = mapped_sub
        stage_cur.execute("SELECT COALESCE(MAX(product_id), 0) + 1 FROM tbl_menu_products")
        new_product_id = stage_cur.fetchone()[0]
        source["product_id"] = new_product_id
        source["menu_id"] = stage_menu_id
        source["user_id"] = stage_user_id
        source["created_at"] = datetime.now(timezone.utc)
        source["updated_at"] = datetime.now(timezone.utc)
        insert_row(stage_cur, "tbl_menu_products", source, stage_product_columns)
        count += 1
    return count


def main() -> None:
    if not PROD["password"]:
        raise SystemExit("Set PROD_DB_PASSWORD")

    prod_conn = psycopg2.connect(**PROD)
    stage_conn = psycopg2.connect(**STAGE)
    prod_conn.autocommit = False
    stage_conn.autocommit = False
    prod_cur = prod_conn.cursor()
    stage_cur = stage_conn.cursor()

    stage_user_id = resolve_stage_user_id(stage_cur, prod_cur)
    summary: list[str] = []

    for prod_menu_id in PROD_MENU_IDS:
        prod_menu = fetch_one_dict(
            prod_cur,
            "SELECT menu_id, public_id, business_name, active, is_deleted FROM tbl_menu WHERE menu_id = %s",
            (prod_menu_id,),
        )
        if prod_menu is None or prod_menu["is_deleted"] or not prod_menu["active"]:
            summary.append(f"skip prod menu {prod_menu_id}")
            continue

        stage_menu_id = ensure_stage_menu_id(stage_cur, prod_cur, prod_menu_id, stage_user_id)
        apply_menu_profile(stage_cur, prod_cur, prod_menu_id, stage_menu_id, stage_user_id)
        clear_stage_catalog(stage_cur, stage_menu_id)
        sub_map = clone_taxonomy(stage_cur, prod_cur, prod_menu_id, stage_menu_id, stage_user_id)
        product_count = clone_products(
            stage_cur, prod_cur, prod_menu_id, stage_menu_id, stage_user_id, sub_map
        )
        summary.append(
            f"prod {prod_menu_id} -> stage {stage_menu_id} public_id={prod_menu['public_id']} products={product_count}"
        )

    stage_conn.commit()
    prod_conn.rollback()
    for line in summary:
        print(line)

    stage_cur.close()
    prod_cur.close()
    stage_conn.close()
    prod_conn.close()


if __name__ == "__main__":
    main()
