from __future__ import annotations

import base64
import secrets
from datetime import datetime, timezone
from io import BytesIO

import psycopg2
from PIL import Image
from psycopg2.extras import Json
import qrcode

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=15,
)
PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
    connect_timeout=15,
)

SOURCE_MENU_ID = 11
TARGET_USER_ID = 25
TARGET_EMAIL = "a11_fen@hotmail.com"
BUSINESS_NAME = "Tarihi Ekmek İçi Sokak Köftecisi"
PUBLIC_BASE = "https://qr.algorycode.com"
USAGE_SOURCE_ORDER = ("PACKAGE_INCLUDE", "ONBOARDING_PACKAGE", "ADDON_PURCHASE")
TABLES = frozenset(
    {
        "tbl_branch",
        "tbl_qr",
        "tbl_menu",
        "tbl_menu_category",
        "tbl_menu_sub_category",
        "tbl_menu_products",
        "tbl_menu_product_option_group",
        "tbl_menu_product_option",
        "tbl_menu_product_tag",
        "tbl_menu_product_allergen",
        "tbl_menu_product_pairing",
    }
)


def now_utc() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def fetch_one(cur, sql: str, params: tuple = ()) -> dict | None:
    cur.execute(sql, params)
    row = cur.fetchone()
    if row is None:
        return None
    columns = [desc[0] for desc in cur.description]
    return dict(zip(columns, row, strict=True))


def fetch_all(cur, sql: str, params: tuple = ()) -> list[dict]:
    cur.execute(sql, params)
    columns = [desc[0] for desc in cur.description]
    return [dict(zip(columns, row, strict=True)) for row in cur.fetchall()]


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


def json_value(value: object) -> object:
    if isinstance(value, (dict, list)):
        return Json(value)
    return value


def insert_row(cur, table_name: str, row: dict[str, object], returning: str) -> object:
    if table_name not in TABLES:
        raise ValueError(table_name)
    columns = list(row.keys())
    placeholders = ", ".join(["%s"] * len(columns))
    column_sql = ", ".join(columns)
    cur.execute(
        f"INSERT INTO {table_name} ({column_sql}) VALUES ({placeholders}) RETURNING {returning}",
        [json_value(row[column]) for column in columns],
    )
    return cur.fetchone()[0]


def generate_public_id(cur) -> str:
    for _ in range(8):
        token = base64.urlsafe_b64encode(secrets.token_bytes(16)).decode("ascii").rstrip("=")
        existing = fetch_one(cur, "SELECT menu_id FROM tbl_menu WHERE public_id = %s", (token,))
        if existing is None:
            return token
    raise RuntimeError("public_id üretilemedi")


def generate_qr_base64(content: str) -> str:
    qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=2)
    qr.add_data(content)
    qr.make(fit=True)
    image = qr.make_image(fill_color="black", back_color="white")
    resized = image.convert("RGB").resize((300, 300), Image.Resampling.NEAREST)
    buffer = BytesIO()
    resized.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("ascii")


def require_stage_menu(stage_cur) -> dict:
    menu = fetch_one(
        stage_cur,
        """
        SELECT *
        FROM tbl_menu
        WHERE menu_id = %s AND is_deleted = false AND business_name = %s
        """,
        (SOURCE_MENU_ID, BUSINESS_NAME),
    )
    if menu is None:
        raise RuntimeError(f"Stage menu {SOURCE_MENU_ID} bulunamadı")
    return menu


def require_prod_user(prod_cur) -> dict:
    user = fetch_one(
        prod_cur,
        "SELECT id, email, first_name, last_name FROM tbl_user WHERE id = %s",
        (TARGET_USER_ID,),
    )
    if user is None or user["email"] != TARGET_EMAIL:
        raise RuntimeError(f"Prod user {TARGET_USER_ID} beklenen {TARGET_EMAIL} değil: {user}")
    return user


def existing_target_menu(prod_cur) -> dict | None:
    return fetch_one(
        prod_cur,
        """
        SELECT menu_id, qr_id, public_id, branch_id, business_name
        FROM tbl_menu
        WHERE user_id = %s AND is_deleted = false AND business_name = %s
        ORDER BY menu_id DESC
        LIMIT 1
        """,
        (TARGET_USER_ID, BUSINESS_NAME),
    )


def ensure_branch(prod_cur, source_branch: dict | None) -> int:
    existing = fetch_one(
        prod_cur,
        """
        SELECT id FROM tbl_branch
        WHERE user_id = %s AND is_deleted = false AND name = %s
        ORDER BY id DESC LIMIT 1
        """,
        (TARGET_USER_ID, BUSINESS_NAME),
    )
    if existing:
        return int(existing["id"])
    now = now_utc()
    payload = {
        "created_at": now,
        "is_deleted": False,
        "updated_at": now,
        "active": True,
        "address": None if source_branch is None else source_branch.get("address"),
        "email": TARGET_EMAIL,
        "grandfathered": False,
        "name": BUSINESS_NAME,
        "phone": None if source_branch is None else source_branch.get("phone"),
        "photo_key": None if source_branch is None else source_branch.get("photo_key"),
        "photo_url": None if source_branch is None else source_branch.get("photo_url"),
        "user_id": TARGET_USER_ID,
    }
    return int(insert_row(prod_cur, "tbl_branch", payload, "id"))


def create_qr(prod_cur, branch_id: int, public_id: str, theme_id: str) -> int:
    public_url = f"{PUBLIC_BASE}/menu/{public_id}"
    now = now_utc()
    details = {
        "email": TARGET_EMAIL,
        "themeId": theme_id,
        "branchId": branch_id,
        "publicUrl": public_url,
        "businessName": BUSINESS_NAME,
    }
    payload = {
        "created_at": now,
        "is_deleted": False,
        "updated_at": now,
        "details": details,
        "img_src": generate_qr_base64(public_url),
        "qr_name": BUSINESS_NAME,
        "qr_type_id": None,
        "customer_id": None,
        "user_id": TARGET_USER_ID,
        "purchase_id": None,
        "active": True,
    }
    return int(insert_row(prod_cur, "tbl_qr", payload, "qr_id"))


def create_menu(prod_cur, source: dict, branch_id: int, qr_id: int, public_id: str) -> int:
    now = now_utc()
    allowed = table_columns(prod_cur, "tbl_menu")
    payload = {key: value for key, value in source.items() if key in allowed}
    payload.update(
        {
            "qr_id": qr_id,
            "public_id": public_id,
            "user_id": TARGET_USER_ID,
            "branch_id": branch_id,
            "email": TARGET_EMAIL,
            "created_at": now,
            "updated_at": now,
            "is_deleted": False,
            "active": True,
            "public_access_enabled": True,
            "rating_avg": 0,
            "rating_count": 0,
            "package_id": None,
        }
    )
    payload.pop("menu_id", None)
    return int(insert_row(prod_cur, "tbl_menu", payload, "menu_id"))


def clone_categories(stage_cur, prod_cur, target_menu_id: int) -> dict[int, int]:
    rows = fetch_all(
        stage_cur,
        """
        SELECT * FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (SOURCE_MENU_ID,),
    )
    allowed = table_columns(prod_cur, "tbl_menu_category")
    mapping: dict[int, int] = {}
    now = now_utc()
    for source in rows:
        old_id = int(source["id"])
        payload = {key: value for key, value in source.items() if key in allowed}
        payload.pop("id", None)
        payload.update(
            {
                "menu_id": target_menu_id,
                "user_id": TARGET_USER_ID,
                "created_at": now,
                "updated_at": now,
                "is_deleted": False,
            }
        )
        mapping[old_id] = int(insert_row(prod_cur, "tbl_menu_category", payload, "id"))
    return mapping


def clone_subcategories(stage_cur, prod_cur, target_menu_id: int, category_map: dict[int, int]) -> dict[int, int]:
    rows = fetch_all(
        stage_cur,
        """
        SELECT * FROM tbl_menu_sub_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY menu_category_id, sort_order, id
        """,
        (SOURCE_MENU_ID,),
    )
    allowed = table_columns(prod_cur, "tbl_menu_sub_category")
    mapping: dict[int, int] = {}
    now = now_utc()
    for source in rows:
        old_id = int(source["id"])
        payload = {key: value for key, value in source.items() if key in allowed}
        payload.pop("id", None)
        payload.update(
            {
                "menu_id": target_menu_id,
                "menu_category_id": category_map[int(source["menu_category_id"])],
                "created_at": now,
                "updated_at": now,
                "is_deleted": False,
            }
        )
        mapping[old_id] = int(insert_row(prod_cur, "tbl_menu_sub_category", payload, "id"))
    return mapping


def clone_products(stage_cur, prod_cur, target_menu_id: int, sub_map: dict[int, int]) -> dict[int, int]:
    rows = fetch_all(
        stage_cur,
        """
        SELECT * FROM tbl_menu_products
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY product_id
        """,
        (SOURCE_MENU_ID,),
    )
    allowed = table_columns(prod_cur, "tbl_menu_products")
    mapping: dict[int, int] = {}
    now = now_utc()
    for source in rows:
        old_id = int(source["product_id"])
        payload = {key: value for key, value in source.items() if key in allowed}
        payload.pop("product_id", None)
        sub_id = source.get("sub_category_id")
        payload.update(
            {
                "menu_id": target_menu_id,
                "sub_category_id": None if sub_id is None else sub_map[int(sub_id)],
                "created_at": now,
                "updated_at": now,
                "is_deleted": False,
                "rating_avg": 0,
                "rating_count": 0,
            }
        )
        mapping[old_id] = int(insert_row(prod_cur, "tbl_menu_products", payload, "product_id"))
    return mapping


def clone_options(stage_cur, prod_cur, product_map: dict[int, int]) -> tuple[int, int]:
    if not product_map:
        return 0, 0
    product_ids = tuple(product_map.keys())
    groups = fetch_all(
        stage_cur,
        """
        SELECT * FROM tbl_menu_product_option_group
        WHERE product_id = ANY(%s)
        ORDER BY product_id, sort_order, id
        """,
        (list(product_ids),),
    )
    if not groups:
        return 0, 0
    allowed_groups = table_columns(prod_cur, "tbl_menu_product_option_group")
    allowed_options = table_columns(prod_cur, "tbl_menu_product_option")
    group_map: dict[int, int] = {}
    for source in groups:
        payload = {key: value for key, value in source.items() if key in allowed_groups}
        payload.pop("id", None)
        payload["product_id"] = product_map[int(source["product_id"])]
        group_map[int(source["id"])] = int(insert_row(prod_cur, "tbl_menu_product_option_group", payload, "id"))
    options = fetch_all(
        stage_cur,
        """
        SELECT * FROM tbl_menu_product_option
        WHERE group_id = ANY(%s)
        ORDER BY group_id, sort_order, id
        """,
        (list(group_map.keys()),),
    )
    for source in options:
        payload = {key: value for key, value in source.items() if key in allowed_options}
        payload.pop("id", None)
        payload["group_id"] = group_map[int(source["group_id"])]
        insert_row(prod_cur, "tbl_menu_product_option", payload, "id")
    return len(group_map), len(options)


def clone_tags_allergens(stage_cur, prod_cur, product_map: dict[int, int]) -> None:
    if not product_map:
        return
    tags = fetch_all(
        stage_cur,
        "SELECT product_id, tag_id FROM tbl_menu_product_tag WHERE product_id = ANY(%s)",
        (list(product_map.keys()),),
    )
    for row in tags:
        prod_cur.execute(
            "INSERT INTO tbl_menu_product_tag (product_id, tag_id) VALUES (%s, %s)",
            (product_map[int(row["product_id"])], row["tag_id"]),
        )
    allergens = fetch_all(
        stage_cur,
        "SELECT product_id, allergen_id FROM tbl_menu_product_allergen WHERE product_id = ANY(%s)",
        (list(product_map.keys()),),
    )
    for row in allergens:
        prod_cur.execute(
            "INSERT INTO tbl_menu_product_allergen (product_id, allergen_id) VALUES (%s, %s)",
            (product_map[int(row["product_id"])], row["allergen_id"]),
        )


def assign_used_quantity(prod_cur, feature_code: str, used_total: int) -> None:
    details = fetch_all(
        prod_cur,
        """
        SELECT id, quantity, unlimited, used_quantity, source
        FROM tbl_fulfillment_detail
        WHERE user_id = %s
          AND feature_code = %s
          AND (expires_at IS NULL OR expires_at > NOW())
        ORDER BY id
        """,
        (TARGET_USER_ID, feature_code),
    )
    by_source: dict[str, list[dict]] = {}
    for detail in details:
        by_source.setdefault(detail["source"], []).append(detail)
    remaining = max(0, used_total)
    for source in USAGE_SOURCE_ORDER:
        for detail in by_source.get(source, []):
            if detail["unlimited"]:
                continue
            capacity = 0 if detail["quantity"] is None else int(detail["quantity"])
            used = min(capacity, remaining)
            prod_cur.execute(
                """
                UPDATE tbl_fulfillment_detail
                SET used_quantity = %s, version = version + 1
                WHERE id = %s
                """,
                (used, detail["id"]),
            )
            remaining -= used


def print_catalog(prod_cur, menu_id: int) -> None:
    rows = fetch_all(
        prod_cur,
        """
        SELECT mc.sort_order, mc.name AS main, sc.name AS sub, p.name, p.price, p.description
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND p.is_deleted = false
        ORDER BY mc.sort_order, sc.sort_order, p.sort_order, p.product_id
        """,
        (menu_id,),
    )
    for row in rows:
        print(f"  [{row['sort_order']}] {row['main']} / {row['sub']} | {row['name']} | {row['price']} | {row['description']}")


def main() -> None:
    stage_conn = psycopg2.connect(**STAGE)
    prod_conn = psycopg2.connect(**PROD)
    stage_conn.autocommit = False
    prod_conn.autocommit = False
    try:
        with stage_conn.cursor() as stage_cur, prod_conn.cursor() as prod_cur:
            source_menu = require_stage_menu(stage_cur)
            require_prod_user(prod_cur)
            already = existing_target_menu(prod_cur)
            if already:
                public_url = f"{PUBLIC_BASE}/menu/{already['public_id']}"
                print(
                    f"SKIP existing prod menu_id={already['menu_id']} qr_id={already['qr_id']} "
                    f"branch_id={already['branch_id']} public={public_url}"
                )
                prod_conn.rollback()
                return
            source_branch = None
            if source_menu.get("branch_id") is not None:
                source_branch = fetch_one(
                    stage_cur,
                    "SELECT * FROM tbl_branch WHERE id = %s",
                    (source_menu["branch_id"],),
                )
            branch_id = ensure_branch(prod_cur, source_branch)
            public_id = generate_public_id(prod_cur)
            qr_id = create_qr(prod_cur, branch_id, public_id, str(source_menu["theme_id"]))
            menu_id = create_menu(prod_cur, source_menu, branch_id, qr_id, public_id)
            category_map = clone_categories(stage_cur, prod_cur, menu_id)
            sub_map = clone_subcategories(stage_cur, prod_cur, menu_id, category_map)
            product_map = clone_products(stage_cur, prod_cur, menu_id, sub_map)
            group_count, option_count = clone_options(stage_cur, prod_cur, product_map)
            clone_tags_allergens(stage_cur, prod_cur, product_map)
            assign_used_quantity(prod_cur, "QR_CREATE", 1)
            assign_used_quantity(prod_cur, "QR_BRANCH", 1)
            assign_used_quantity(prod_cur, "MENU_PRODUCT", len(product_map))
            prod_conn.commit()
            public_url = f"{PUBLIC_BASE}/menu/{public_id}"
            print(
                f"OK user_id={TARGET_USER_ID} branch_id={branch_id} menu_id={menu_id} "
                f"qr_id={qr_id} cats={len(category_map)} subs={len(sub_map)} "
                f"products={len(product_map)} option_groups={group_count} options={option_count}"
            )
            print(f"public={public_url}")
            print_catalog(prod_cur, menu_id)
    except Exception:
        prod_conn.rollback()
        raise
    finally:
        stage_conn.rollback()
        stage_conn.close()
        prod_conn.close()


if __name__ == "__main__":
    main()
