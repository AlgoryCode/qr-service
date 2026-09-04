from __future__ import annotations

from datetime import datetime

import psycopg2
from psycopg2.extras import Json

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)
PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

SOURCE_MENU_ID = 17
TARGET_EMAIL = "ulasbayram61@gmail.com"


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
    cur.execute(
        f"INSERT INTO {table_name} ({column_sql}) VALUES ({placeholders})",
        values,
    )


def ensure_stage_user(stage_cur, prod_cur) -> int:
    existing = fetch_one_dict(
        stage_cur,
        "SELECT id FROM tbl_user WHERE email = %s",
        (TARGET_EMAIL,),
    )
    if existing:
        return existing["id"]

    prod_user = fetch_one_dict(
        prod_cur,
        "SELECT * FROM tbl_user WHERE email = %s",
        (TARGET_EMAIL,),
    )
    if prod_user is None:
        raise RuntimeError(f"Prod user not found for {TARGET_EMAIL}")

    stage_cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_user")
    new_user_id = stage_cur.fetchone()[0]

    stage_user_columns = table_columns(stage_cur, "tbl_user")
    columns = [key for key in prod_user if key != "id" and key in stage_user_columns]
    payload = {key: prod_user[key] for key in columns}
    payload["id"] = new_user_id
    insert_row(stage_cur, "tbl_user", payload, stage_user_columns)
    return new_user_id


def clone_menu(stage_cur, prod_cur, stage_user_id: int) -> int:
    existing = fetch_one_dict(
        stage_cur,
        """
        SELECT m.menu_id
        FROM tbl_menu m
        JOIN tbl_user u ON u.id = m.user_id
        WHERE u.email = %s AND m.is_deleted = false
        LIMIT 1
        """,
        (TARGET_EMAIL,),
    )
    if existing:
        return existing["menu_id"]

    prod_menu = fetch_one_dict(
        prod_cur,
        "SELECT * FROM tbl_menu WHERE menu_id = %s",
        (SOURCE_MENU_ID,),
    )
    if prod_menu is None:
        raise RuntimeError(f"Prod menu {SOURCE_MENU_ID} not found")

    stage_cur.execute("SELECT COALESCE(MAX(menu_id), 0) + 1 FROM tbl_menu")
    new_menu_id = stage_cur.fetchone()[0]
    stage_cur.execute("SELECT COALESCE(MAX(qr_id), 0) + 1 FROM tbl_menu")
    new_qr_id = stage_cur.fetchone()[0]

    menu_columns = table_columns(stage_cur, "tbl_menu")
    prod_menu["menu_id"] = new_menu_id
    prod_menu["qr_id"] = new_qr_id
    prod_menu["user_id"] = stage_user_id
    prod_menu["branch_id"] = None
    prod_menu["created_at"] = datetime.utcnow()
    prod_menu["updated_at"] = datetime.utcnow()
    insert_row(stage_cur, "tbl_menu", prod_menu, menu_columns)
    return new_menu_id


def clone_taxonomy(stage_cur, prod_cur, stage_menu_id: int, stage_user_id: int) -> dict[int, int]:
    prod_cur.execute(
        """
        SELECT *
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (SOURCE_MENU_ID,),
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
        source["created_at"] = datetime.utcnow()
        source["updated_at"] = datetime.utcnow()
        insert_row(stage_cur, "tbl_menu_category", source, stage_category_columns)
        category_map[old_id] = new_id

    prod_cur.execute(
        """
        SELECT *
        FROM tbl_menu_sub_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY menu_category_id, sort_order, id
        """,
        (SOURCE_MENU_ID,),
    )
    prod_sub_columns = [desc[0] for desc in prod_cur.description]
    stage_sub_columns = table_columns(stage_cur, "tbl_menu_sub_category")
    sub_map: dict[int, int] = {}

    for row in prod_cur.fetchall():
        source = dict(zip(prod_sub_columns, row, strict=True))
        old_id = source["id"]
        stage_cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM tbl_menu_sub_category")
        new_id = stage_cur.fetchone()[0]
        source["id"] = new_id
        source["menu_id"] = stage_menu_id
        source["menu_category_id"] = category_map[source["menu_category_id"]]
        source["created_at"] = datetime.utcnow()
        source["updated_at"] = datetime.utcnow()
        insert_row(stage_cur, "tbl_menu_sub_category", source, stage_sub_columns)
        sub_map[old_id] = new_id

    return sub_map


def clone_products(stage_cur, prod_cur, stage_menu_id: int, stage_user_id: int, sub_map: dict[int, int]) -> int:
    prod_cur.execute(
        """
        SELECT *
        FROM tbl_menu_products
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY product_id
        """,
        (SOURCE_MENU_ID,),
    )
    prod_product_columns = [desc[0] for desc in prod_cur.description]
    stage_product_columns = table_columns(stage_cur, "tbl_menu_products")
    count = 0

    for row in prod_cur.fetchall():
        source = dict(zip(prod_product_columns, row, strict=True))
        stage_cur.execute("SELECT COALESCE(MAX(product_id), 0) + 1 FROM tbl_menu_products")
        new_product_id = stage_cur.fetchone()[0]
        source["product_id"] = new_product_id
        source["menu_id"] = stage_menu_id
        source["user_id"] = stage_user_id
        if source.get("sub_category_id") is not None:
            source["sub_category_id"] = sub_map[source["sub_category_id"]]
        source["created_at"] = datetime.utcnow()
        source["updated_at"] = datetime.utcnow()
        insert_row(stage_cur, "tbl_menu_products", source, stage_product_columns)
        count += 1
    return count


def main() -> None:
    prod_conn = psycopg2.connect(**PROD)
    stage_conn = psycopg2.connect(**STAGE)
    prod_conn.autocommit = False
    stage_conn.autocommit = False
    prod_cur = prod_conn.cursor()
    stage_cur = stage_conn.cursor()

    stage_user_id = ensure_stage_user(stage_cur, prod_cur)
    stage_menu_id = clone_menu(stage_cur, prod_cur, stage_user_id)
    sub_map = clone_taxonomy(stage_cur, prod_cur, stage_menu_id, stage_user_id)
    product_count = clone_products(stage_cur, prod_cur, stage_menu_id, stage_user_id, sub_map)

    stage_conn.commit()
    prod_conn.rollback()

    print(
        f"STAGE user_id={stage_user_id} menu_id={stage_menu_id} "
        f"subs={len(sub_map)} products={product_count}"
    )

    stage_cur.close()
    prod_cur.close()
    stage_conn.close()
    prod_conn.close()


if __name__ == "__main__":
    main()
