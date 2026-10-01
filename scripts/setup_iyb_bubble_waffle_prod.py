from __future__ import annotations

import base64
import secrets
from datetime import datetime, timezone
from io import BytesIO
from typing import Any

import psycopg2
from psycopg2.extras import Json, RealDictCursor

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
    connect_timeout=15,
)

USER_ID = 29
USER_EMAIL = "ahmetincesu97@gmail.com"
BUSINESS_NAME = "IYB Bubble Waffle"
BRANCH_NAME = "IYB Bubble Waffle Küçükçekmece"
THEME_ID = "luxury"
PHONE = "05441383404"
ADDRESS = "Küçükçekmece"
SLOGAN = "Lezzetin bubble hali!"
PUBLIC_BASE = "https://qr.algorycode.com"

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
    }
)

WAFFLE_OPTION_PRODUCTS = frozenset(
    {
        "Bardakta (Bubble Waffle)",
        "Açık Bubble Waffle",
        "Çiçek Waffle",
        "Çubuk Waffle",
        "Fondü Waffle",
        "Kova Waffle",
        "Waffle Cup",
        "Ice Chocolate Banana",
        "Strawberry Chocolate",
        "Fruity Cup",
    }
)

CATEGORIES: list[tuple[str, str, list[tuple[str, str, list[tuple[str, float]]]]]] = [
    (
        "waffle",
        "Waffle",
        [
            (
                "waffle",
                "Waffle",
                [
                    ("Bardakta (Bubble Waffle)", 250),
                    ("Açık Bubble Waffle", 400),
                    ("Çiçek Waffle", 350),
                    ("Çubuk Waffle", 290),
                    ("Fondü Waffle", 450),
                ],
            ),
        ],
    ),
    (
        "cuplar",
        "Cuplar",
        [
            (
                "cuplar",
                "Cuplar",
                [
                    ("Kova Waffle", 280),
                    ("Waffle Cup", 220),
                    ("Ice Chocolate Banana", 120),
                    ("Strawberry Chocolate", 120),
                    ("Fruity Cup", 150),
                ],
            ),
        ],
    ),
    (
        "sicak_icecekler",
        "Sıcak İçecekler",
        [
            (
                "sicak_icecekler",
                "Sıcak İçecekler",
                [
                    ("Çay", 40),
                    ("Fincan Çay", 70),
                    ("Türk Kahvesi", 100),
                    ("Double Türk Kahvesi", 130),
                    ("Latte", 120),
                    ("Americano", 120),
                    ("Espresso", 90),
                    ("Cappuccino", 120),
                    ("Macchiato", 120),
                ],
            ),
        ],
    ),
    (
        "soguk_icecekler",
        "Soğuk İçecekler",
        [
            (
                "soguk_icecekler",
                "Soğuk İçecekler",
                [
                    ("Kola", 70),
                    ("Sade Soda", 40),
                    ("Meyveli Soda", 50),
                    ("Ice Tea", 70),
                    ("Su", 30),
                    ("Ice Latte", 140),
                    ("Ice Americano", 140),
                    ("Ice Cappuccino", 140),
                    ("Ice Macchiato", 140),
                    ("Limonata", 100),
                ],
            ),
        ],
    ),
    (
        "dondurma",
        "Dondurma",
        [
            (
                "dondurma",
                "Dondurma",
                [
                    ("Sade", 50),
                    ("Kakao", 50),
                    ("Çilek", 50),
                    ("Karamel", 50),
                    ("Limon", 50),
                    ("Antep Fıstıklı", 50),
                ],
            ),
        ],
    ),
]

WAFFLE_OPTION_GROUPS: list[dict[str, Any]] = [
    {
        "name": "Çikolatalar",
        "kind": "EXTRA",
        "unit": "NONE",
        "min_select": 0,
        "max_select": 7,
        "sort_order": 0,
        "options": [
            ("Sütlü", 0),
            ("Beyaz", 0),
            ("Bitter", 0),
            ("Belçika", 70),
            ("Karamel Sos", 40),
            ("Frambuaz Sos", 40),
            ("Antep Sos", 50),
        ],
    },
    {
        "name": "Meyveler",
        "kind": "EXTRA",
        "unit": "NONE",
        "min_select": 0,
        "max_select": 3,
        "sort_order": 1,
        "options": [
            ("Çilek", 0),
            ("Muz", 0),
            ("Kivi", 0),
        ],
    },
    {
        "name": "Şekerlemeler",
        "kind": "EXTRA",
        "unit": "NONE",
        "min_select": 0,
        "max_select": 16,
        "sort_order": 2,
        "options": [
            ("Hindistan Cevizi", 0),
            ("Sütlü Pirinç Patlağı", 0),
            ("Beyaz Damla Çikolata", 0),
            ("Sütlü Damla Çikolata", 0),
            ("Frambuaz Damla Çikolata", 0),
            ("Beyaz Pirinç Patlağı", 0),
            ("Oreo Toz", 0),
            ("Bronz Pirinç Patlağı", 0),
            ("Antep Fıstığı", 0),
            ("Fındık", 0),
            ("Bonibon", 0),
            ("Çakıl Taşı", 0),
            ("Lotus", 0),
            ("Cicibebe", 0),
            ("Pasta Süsü", 0),
            ("Frambuaz Pirinç Patlağı", 0),
        ],
    },
    {
        "name": "Dondurma",
        "kind": "EXTRA",
        "unit": "NONE",
        "min_select": 0,
        "max_select": 4,
        "sort_order": 3,
        "options": [
            ("Sade", 50),
            ("Kakao", 50),
            ("Karamel", 50),
            ("Çilek", 50),
        ],
    },
]


def now_utc() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def fetch_one(cur, sql: str, params: tuple[object, ...] = ()) -> dict | None:
    cur.execute(sql, params)
    row = cur.fetchone()
    if row is None:
        return None
    return dict(row)


def fetch_all(cur, sql: str, params: tuple[object, ...] = ()) -> list[dict]:
    cur.execute(sql, params)
    return [dict(row) for row in cur.fetchall()]


def table_columns(cur, table_name: str) -> set[str]:
    cur.execute(
        """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = %s
        """,
        (table_name,),
    )
    return {row["column_name"] for row in cur.fetchall()}


def json_value(value: object) -> object:
    if isinstance(value, (dict, list)):
        return Json(value)
    return value


def insert_row(cur, table_name: str, row: dict[str, object], returning: str) -> object:
    if table_name not in TABLES:
        raise ValueError(table_name)
    allowed = table_columns(cur, table_name)
    payload = {key: value for key, value in row.items() if key in allowed}
    columns = list(payload.keys())
    placeholders = ", ".join(["%s"] * len(columns))
    column_sql = ", ".join(columns)
    cur.execute(
        f"INSERT INTO {table_name} ({column_sql}) VALUES ({placeholders}) RETURNING {returning}",
        [json_value(payload[column]) for column in columns],
    )
    return cur.fetchone()[returning]


def generate_public_id(cur) -> str:
    for _ in range(8):
        token = base64.urlsafe_b64encode(secrets.token_bytes(16)).decode("ascii").rstrip("=")
        existing = fetch_one(cur, "SELECT menu_id FROM tbl_menu WHERE public_id = %s", (token,))
        if existing is None:
            return token
    raise RuntimeError("public_id üretilemedi")


def generate_qr_base64(content: str) -> str | None:
    try:
        import qrcode
        from PIL import Image
    except ImportError:
        return None
    qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=2)
    qr.add_data(content)
    qr.make(fit=True)
    image = qr.make_image(fill_color="black", back_color="white")
    resized = image.convert("RGB").resize((300, 300), Image.Resampling.NEAREST)
    buffer = BytesIO()
    resized.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("ascii")


def require_user(cur) -> dict:
    user = fetch_one(
        cur,
        "SELECT id, email, first_name, last_name FROM tbl_user WHERE id = %s",
        (USER_ID,),
    )
    if user is None or user["email"] != USER_EMAIL:
        raise RuntimeError(f"Prod user {USER_ID} beklenen {USER_EMAIL} değil: {user}")
    return user


def existing_menu(cur) -> dict | None:
    return fetch_one(
        cur,
        """
        SELECT menu_id, qr_id, public_id, branch_id, business_name
        FROM tbl_menu
        WHERE user_id = %s AND is_deleted = false AND business_name = %s
        ORDER BY menu_id DESC
        LIMIT 1
        """,
        (USER_ID, BUSINESS_NAME),
    )


def ensure_branch(cur) -> int:
    existing = fetch_one(
        cur,
        """
        SELECT id FROM tbl_branch
        WHERE user_id = %s AND is_deleted = false AND name = %s
        ORDER BY id DESC LIMIT 1
        """,
        (USER_ID, BRANCH_NAME),
    )
    if existing:
        return int(existing["id"])
    now = now_utc()
    return int(
        insert_row(
            cur,
            "tbl_branch",
            {
                "created_at": now,
                "is_deleted": False,
                "updated_at": now,
                "active": True,
                "address": ADDRESS,
                "email": USER_EMAIL,
                "grandfathered": False,
                "name": BRANCH_NAME,
                "phone": PHONE,
                "photo_key": None,
                "photo_url": None,
                "user_id": USER_ID,
                "kitchen_enabled": False,
                "print_kitchen_enabled": False,
            },
            "id",
        )
    )


def create_qr(cur, branch_id: int, public_id: str) -> int:
    public_url = f"{PUBLIC_BASE}/menu/{public_id}"
    now = now_utc()
    details = {
        "email": USER_EMAIL,
        "themeId": THEME_ID,
        "branchId": branch_id,
        "publicUrl": public_url,
        "businessName": BUSINESS_NAME,
        "phone": PHONE,
        "address": ADDRESS,
        "slogan": SLOGAN,
    }
    return int(
        insert_row(
            cur,
            "tbl_qr",
            {
                "created_at": now,
                "is_deleted": False,
                "updated_at": now,
                "details": details,
                "img_src": generate_qr_base64(public_url),
                "qr_name": BUSINESS_NAME,
                "qr_type_id": None,
                "customer_id": None,
                "user_id": USER_ID,
                "purchase_id": None,
                "active": True,
            },
            "qr_id",
        )
    )


def create_menu(cur, branch_id: int, qr_id: int, public_id: str) -> int:
    now = now_utc()
    return int(
        insert_row(
            cur,
            "tbl_menu",
            {
                "created_at": now,
                "is_deleted": False,
                "updated_at": now,
                "active": True,
                "address": ADDRESS,
                "business_name": BUSINESS_NAME,
                "email": USER_EMAIL,
                "package_id": None,
                "phone": PHONE,
                "qr_id": qr_id,
                "theme_id": THEME_ID,
                "user_id": USER_ID,
                "chef_avatar_key": None,
                "chef_name": None,
                "logo_key": None,
                "logo_url": None,
                "public_access_disabled_reason": None,
                "public_access_enabled": True,
                "rating_avg": 0,
                "rating_count": 0,
                "slogan": SLOGAN,
                "branch_id": branch_id,
                "public_id": public_id,
            },
            "menu_id",
        )
    )


def upsert_main(cur, menu_id: int, slug: str, name: str, sort_order: int) -> int:
    now = now_utc()
    row = fetch_one(
        cur,
        """
        SELECT id FROM tbl_menu_category
        WHERE menu_id = %s AND slug = %s
        ORDER BY is_deleted ASC, id ASC LIMIT 1
        """,
        (menu_id, slug),
    )
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET is_deleted = false, name = %s, sort_order = %s, updated_at = %s, user_id = %s
            WHERE id = %s
            """,
            (name, sort_order, now, USER_ID, row["id"]),
        )
        return int(row["id"])
    return int(
        insert_row(
            cur,
            "tbl_menu_category",
            {
                "menu_id": menu_id,
                "user_id": USER_ID,
                "slug": slug,
                "name": name,
                "sort_order": sort_order,
                "created_at": now,
                "updated_at": now,
                "is_deleted": False,
            },
            "id",
        )
    )


def upsert_sub(cur, menu_id: int, main_id: int, slug: str, name: str, sort_order: int) -> int:
    now = now_utc()
    row = fetch_one(
        cur,
        """
        SELECT id FROM tbl_menu_sub_category
        WHERE menu_id = %s AND slug = %s
        ORDER BY is_deleted ASC, id ASC LIMIT 1
        """,
        (menu_id, slug),
    )
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET is_deleted = false, menu_category_id = %s, name = %s,
                sort_order = %s, updated_at = %s
            WHERE id = %s
            """,
            (main_id, name, sort_order, now, row["id"]),
        )
        return int(row["id"])
    return int(
        insert_row(
            cur,
            "tbl_menu_sub_category",
            {
                "menu_id": menu_id,
                "menu_category_id": main_id,
                "slug": slug,
                "name": name,
                "sort_order": sort_order,
                "created_at": now,
                "updated_at": now,
                "is_deleted": False,
            },
            "id",
        )
    )


def upsert_product(cur, menu_id: int, sub_id: int, name: str, price: float, sort_order: int) -> int:
    now = now_utc()
    row = fetch_one(
        cur,
        """
        SELECT product_id FROM tbl_menu_products
        WHERE menu_id = %s AND name = %s AND is_deleted = false
        ORDER BY product_id ASC LIMIT 1
        """,
        (menu_id, name),
    )
    if row:
        cur.execute(
            """
            UPDATE tbl_menu_products
            SET sub_category_id = %s, price = %s, currency = 'TRY',
                sort_order = %s, available = true, updated_at = %s
            WHERE product_id = %s
            """,
            (sub_id, price, sort_order, now, row["product_id"]),
        )
        return int(row["product_id"])
    return int(
        insert_row(
            cur,
            "tbl_menu_products",
            {
                "created_at": now,
                "is_deleted": False,
                "updated_at": now,
                "available": True,
                "currency": "TRY",
                "description": None,
                "image_url": None,
                "menu_id": menu_id,
                "name": name,
                "price": price,
                "sort_order": sort_order,
                "chef_recommended": False,
                "nutrition": {},
                "rating_avg": 0,
                "rating_count": 0,
                "serves_people_max": None,
                "serves_people_min": None,
                "sub_category_id": sub_id,
            },
            "product_id",
        )
    )


def replace_waffle_options(cur, product_id: int) -> tuple[int, int]:
    cur.execute(
        "DELETE FROM tbl_menu_product_option_group WHERE product_id = %s",
        (product_id,),
    )
    group_count = 0
    option_count = 0
    for group in WAFFLE_OPTION_GROUPS:
        group_id = int(
            insert_row(
                cur,
                "tbl_menu_product_option_group",
                {
                    "product_id": product_id,
                    "name": group["name"],
                    "kind": group["kind"],
                    "unit": group["unit"],
                    "min_select": group["min_select"],
                    "max_select": group["max_select"],
                    "sort_order": group["sort_order"],
                },
                "id",
            )
        )
        group_count += 1
        for option_order, (option_name, price_delta) in enumerate(group["options"]):
            insert_row(
                cur,
                "tbl_menu_product_option",
                {
                    "group_id": group_id,
                    "name": option_name,
                    "price_delta": price_delta,
                    "available": True,
                    "sort_order": option_order,
                },
                "id",
            )
            option_count += 1
    return group_count, option_count


def seed_catalog(cur, menu_id: int) -> tuple[int, int, int, int]:
    product_ids: list[tuple[int, str]] = []
    category_count = 0
    sub_count = 0
    for main_order, (main_slug, main_name, subs) in enumerate(CATEGORIES):
        main_id = upsert_main(cur, menu_id, main_slug, main_name, main_order)
        category_count += 1
        for sub_order, (sub_slug, sub_name, products) in enumerate(subs):
            unique_sub_slug = sub_slug if main_slug == sub_slug else f"{main_slug}_{sub_slug}"
            sub_id = upsert_sub(cur, menu_id, main_id, unique_sub_slug, sub_name, sub_order)
            sub_count += 1
            for prod_order, (name, price) in enumerate(products):
                product_id = upsert_product(cur, menu_id, sub_id, name, price, prod_order * 10)
                product_ids.append((product_id, name))
    option_groups = 0
    options = 0
    for product_id, name in product_ids:
        if name not in WAFFLE_OPTION_PRODUCTS:
            continue
        group_count, option_count = replace_waffle_options(cur, product_id)
        option_groups += group_count
        options += option_count
    return category_count, sub_count, len(product_ids), option_groups


def print_catalog(cur, menu_id: int) -> None:
    rows = fetch_all(
        cur,
        """
        SELECT mc.sort_order AS main_sort, mc.name AS main, sc.name AS sub,
               p.product_id, p.name, p.price
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND p.is_deleted = false
        ORDER BY mc.sort_order, sc.sort_order, p.sort_order, p.product_id
        """,
        (menu_id,),
    )
    for row in rows:
        print(
            f"  [{row['main_sort']}] {row['main']} / {row['sub']} | "
            f"{row['name']} | {row['price']} | product_id={row['product_id']}"
        )
    option_rows = fetch_all(
        cur,
        """
        SELECT p.name AS product, g.name AS group_name, g.kind, g.min_select, g.max_select,
               o.name AS option_name, o.price_delta
        FROM tbl_menu_product_option o
        JOIN tbl_menu_product_option_group g ON g.id = o.group_id
        JOIN tbl_menu_products p ON p.product_id = g.product_id
        WHERE p.menu_id = %s
        ORDER BY p.sort_order, p.product_id, g.sort_order, o.sort_order
        """,
        (menu_id,),
    )
    print(f"option_rows={len(option_rows)}")
    seen: set[str] = set()
    for row in option_rows:
        key = f"{row['product']}|{row['group_name']}"
        if key in seen:
            continue
        seen.add(key)
        print(
            f"  options {row['product']} / {row['group_name']} "
            f"kind={row['kind']} min={row['min_select']} max={row['max_select']}"
        )


def main() -> None:
    conn = psycopg2.connect(**PROD)
    conn.autocommit = False
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            require_user(cur)
            already = existing_menu(cur)
            if already:
                public_url = f"{PUBLIC_BASE}/menu/{already['public_id']}"
                print(
                    f"SKIP existing prod menu_id={already['menu_id']} qr_id={already['qr_id']} "
                    f"branch_id={already['branch_id']} public={public_url}"
                )
                conn.rollback()
                return
            branch_id = ensure_branch(cur)
            public_id = generate_public_id(cur)
            qr_id = create_qr(cur, branch_id, public_id)
            menu_id = create_menu(cur, branch_id, qr_id, public_id)
            cats, subs, products, option_groups = seed_catalog(cur, menu_id)
            conn.commit()
            public_url = f"{PUBLIC_BASE}/menu/{public_id}"
            print(
                f"OK user_id={USER_ID} branch_id={branch_id} menu_id={menu_id} "
                f"qr_id={qr_id} cats={cats} subs={subs} products={products} "
                f"option_groups={option_groups}"
            )
            print(f"public={public_url}")
            print_catalog(cur, menu_id)
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
