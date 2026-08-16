#!/usr/bin/env python3
"""Reset trkhamarat@gmail.com billing state and restore İPEK KAFE menu from backup."""

from __future__ import annotations

import json
import os
from datetime import datetime, timedelta
from pathlib import Path

import psycopg2
import psycopg2.extras

USER_ID = 1
USER_EMAIL = "trkhamarat@gmail.com"
PACKAGE_CODE = "PRO_PACKAGE"
PACKAGE_NAME = "Pro"
PURCHASE_TYPE = "SYSTEM_GRANT"
PAYMENT_STYLE = "ONE_TIME"
VALIDITY_DAYS = 30

SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR / "data"
PRODUCTS_BACKUP = DATA_DIR / "trkhamarat-menu-8-products-backup.json"
PROFILE_BACKUP = DATA_DIR / "trkhamarat-menu-8-profile-backup.json"


def connect():
    return psycopg2.connect(
        host=os.environ.get("DB_HOST", "185.184.210.52"),
        port=os.environ.get("DB_PORT", "5432"),
        dbname=os.environ.get("DB_NAME", "algoryqrdb"),
        user=os.environ.get("DB_USERNAME", "postgres"),
        password=os.environ.get(
            "DB_PASSWORD",
            "AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
        ),
    )


def load_json(path: Path):
    text = path.read_text(encoding="utf-8").strip()
    return json.loads(text)


def delete_user_purchases(cur, user_id: int) -> list[int]:
    cur.execute("SELECT id FROM tbl_purchase WHERE user_id = %s", (user_id,))
    purchase_ids = [row[0] for row in cur.fetchall()]
    if not purchase_ids:
        return purchase_ids

    cur.execute("UPDATE tbl_qr SET purchase_id = NULL WHERE user_id = %s", (user_id,))

    for table, column in [
        ("tbl_user_entitlement", "purchase_id"),
        ("tbl_purchase_log", "purchase_id"),
        ("tbl_purchase_fulfillment", "purchase_id"),
        ("tbl_purchase_reminder", "purchase_id"),
        ("tbl_payment_event_inbox", "purchase_id"),
        ("tbl_processed_payment_event", "purchase_id"),
    ]:
        cur.execute(
            f"DELETE FROM {table} WHERE {column} = ANY(%s)",
            (purchase_ids,),
        )

    cur.execute(
        """
        DELETE FROM tbl_plan_change
        WHERE from_purchase_id = ANY(%s)
           OR resulting_purchase_id = ANY(%s)
        """,
        (purchase_ids, purchase_ids),
    )
    cur.execute("DELETE FROM tbl_purchase WHERE user_id = %s", (user_id,))
    return purchase_ids


def create_fresh_purchase(cur, user_id: int) -> tuple[int, dict]:
    cur.execute(
        "SELECT id, name, currency, validity_days FROM tbl_plan_package WHERE code = %s",
        (PACKAGE_CODE,),
    )
    package = cur.fetchone()
    if not package:
        raise RuntimeError(f"Package not found: {PACKAGE_CODE}")
    package_id, package_name, currency, validity_days = package
    starts_at = datetime.now()
    expires_at = starts_at + timedelta(days=validity_days or VALIDITY_DAYS)

    cur.execute(
        """
        INSERT INTO tbl_purchase (
            user_id, package_id, package_code, package_name, price, currency,
            status, purchase_type, payment_style, payment_mode,
            starts_at, expires_at, purchased_at
        ) VALUES (
            %s, %s, %s, %s, 0, %s,
            'ACTIVE', %s, %s, 'THREE_DS',
            %s, %s, %s
        )
        RETURNING id
        """,
        (
            user_id,
            package_id,
            PACKAGE_CODE,
            package_name,
            currency,
            PURCHASE_TYPE,
            PAYMENT_STYLE,
            starts_at,
            expires_at,
            starts_at,
        ),
    )
    purchase_id = cur.fetchone()[0]

    cur.execute(
        """
        SELECT p.id, p.code, ppi.quantity, ppi.unlimited
        FROM tbl_plan_package_item ppi
        JOIN tbl_product p ON p.id = ppi.product_id
        WHERE ppi.package_id = %s
        ORDER BY p.code
        """,
        (package_id,),
    )
    items = cur.fetchall()
    entitlements: dict[str, dict] = {}
    for product_id, product_code, quantity, unlimited in items:
        total = quantity or 0
        cur.execute(
            """
            INSERT INTO tbl_user_entitlement (
                user_id, product_id, product_code, purchase_id,
                total_quantity, remaining_quantity, used_quantity,
                unlimited, starts_at, expires_at, created_at, updated_at
            ) VALUES (
                %s, %s, %s, %s,
                %s, %s, 0,
                %s, %s, %s, NOW(), NOW()
            )
            """,
            (
                user_id,
                product_id,
                product_code,
                purchase_id,
                total,
                total,
                unlimited,
                starts_at,
                expires_at,
            ),
        )
        entitlements[product_code] = {
            "total": total,
            "remaining": total,
            "unlimited": unlimited,
        }

    return purchase_id, entitlements


def create_menu_qr(cur, user_id: int, purchase_id: int, profile: dict) -> tuple[int, int]:
    details = profile.get("qr_details") or {}
    if isinstance(details, str):
        details = json.loads(details)
    details = dict(details)
    details.pop("products", None)
    details["businessName"] = profile["business_name"]
    details["themeId"] = profile["theme_id"]
    details["slogan"] = profile.get("slogan")
    details["phone"] = profile.get("phone")
    details["email"] = profile.get("email")
    details["address"] = profile.get("address")

    cur.execute(
        """
        INSERT INTO tbl_qr (
            user_id, purchase_id, qr_name, img_src, details, active, is_deleted, created_at, updated_at
        ) VALUES (
            %s, %s, %s, %s, %s::jsonb, TRUE, FALSE, NOW(), NOW()
        )
        RETURNING qr_id
        """,
        (
            user_id,
            purchase_id,
            profile["business_name"],
            profile.get("qr_img_src"),
            json.dumps(details, ensure_ascii=False),
        ),
    )
    qr_id = cur.fetchone()[0]

    cur.execute(
        """
        INSERT INTO tbl_menu (
            qr_id, user_id, theme_id, business_name, slogan, chef_name, chef_avatar_key,
            phone, email, address, logo_url, logo_key, active, is_deleted,
            public_access_enabled, rating_avg, rating_count, created_at, updated_at
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s, TRUE, FALSE,
            TRUE, %s, %s, NOW(), NOW()
        )
        RETURNING menu_id
        """,
        (
            qr_id,
            user_id,
            profile["theme_id"],
            profile["business_name"],
            profile.get("slogan"),
            profile.get("chef_name"),
            profile.get("chef_avatar_key") or "default",
            profile.get("phone"),
            profile.get("email"),
            profile.get("address"),
            profile.get("logo_url"),
            profile.get("logo_key"),
            profile.get("rating_avg") or 0,
            profile.get("rating_count") or 0,
        ),
    )
    menu_id = cur.fetchone()[0]

    public_url = f"http://localhost:3000/menu/{qr_id}"
    cur.execute(
        """
        UPDATE tbl_qr
        SET details = jsonb_set(COALESCE(details, '{}'::jsonb), '{publicUrl}', to_jsonb(%s::text), true),
            updated_at = NOW()
        WHERE qr_id = %s
        """,
        (public_url, qr_id),
    )
    return qr_id, menu_id


def import_products(cur, menu_id: int, products: list[dict]) -> int:
    imported = 0
    for item in products:
        cur.execute(
            """
            INSERT INTO tbl_menu_products (
                menu_id, name, description, price, currency, sub_category_id, sort_order,
                image_url, available, chef_recommended, rating_avg, rating_count,
                serves_people_min, serves_people_max, nutrition, is_deleted, created_at, updated_at
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s,
                %s, %s, %s::jsonb, FALSE, NOW(), NOW()
            )
            RETURNING product_id
            """,
            (
                menu_id,
                item["name"],
                item.get("description"),
                item.get("price"),
                item.get("currency") or "TRY",
                item["sub_category_id"],
                item.get("sort_order") or 0,
                item.get("image_url"),
                item.get("available", True),
                item.get("chef_recommended", False),
                item.get("rating_avg") or 0,
                item.get("rating_count") or 0,
                item.get("serves_people_min"),
                item.get("serves_people_max"),
                json.dumps(item.get("nutrition") or {}),
            ),
        )
        product_id = cur.fetchone()[0]

        tag_ids = item.get("tag_ids") or []
        if isinstance(tag_ids, list):
            for tag_id in tag_ids:
                if tag_id is not None:
                    cur.execute(
                        "INSERT INTO tbl_menu_product_tag (product_id, tag_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
                        (product_id, tag_id),
                    )

        allergen_ids = item.get("allergen_ids") or []
        if isinstance(allergen_ids, list):
            for allergen_id in allergen_ids:
                if allergen_id is not None:
                    cur.execute(
                        "INSERT INTO tbl_menu_product_allergen (product_id, allergen_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
                        (product_id, allergen_id),
                    )
        imported += 1
    return imported


def sync_entitlement_usage(cur, user_id: int, purchase_id: int) -> None:
    cur.execute(
        """
        UPDATE tbl_user_entitlement
        SET used_quantity = 1,
            remaining_quantity = GREATEST(total_quantity - 1, 0),
            updated_at = NOW()
        WHERE user_id = %s
          AND purchase_id = %s
          AND product_code = 'QR_CREATE'
          AND unlimited = FALSE
        """,
        (user_id, purchase_id),
    )
    cur.execute(
        """
        UPDATE tbl_user_entitlement
        SET used_quantity = 1,
            remaining_quantity = 0,
            updated_at = NOW()
        WHERE user_id = %s
          AND purchase_id = %s
          AND product_code = 'QR_MENU'
          AND unlimited = FALSE
        """,
        (user_id, purchase_id),
    )


def main() -> None:
    products = load_json(PRODUCTS_BACKUP)
    profile = load_json(PROFILE_BACKUP)
    if not isinstance(products, list):
        raise RuntimeError("Products backup must be a JSON array")

    with connect() as conn:
        conn.autocommit = False
        with conn.cursor() as cur:
            cur.execute("SELECT id, email FROM tbl_user WHERE id = %s", (USER_ID,))
            user = cur.fetchone()
            if not user:
                raise RuntimeError(f"User {USER_ID} not found")

            deleted_purchases = delete_user_purchases(cur, USER_ID)
            purchase_id, entitlements = create_fresh_purchase(cur, USER_ID)
            qr_id, menu_id = create_menu_qr(cur, USER_ID, purchase_id, profile)
            imported = import_products(cur, menu_id, products)
            sync_entitlement_usage(cur, USER_ID, purchase_id)

        conn.commit()

    print("Reset completed for", USER_EMAIL)
    print(f"- Deleted purchases: {deleted_purchases}")
    print(f"- New purchase id: {purchase_id} ({PACKAGE_CODE}, {PURCHASE_TYPE})")
    print(f"- Entitlements: {json.dumps(entitlements, ensure_ascii=False)}")
    print(f"- New QR id: {qr_id}")
    print(f"- New menu id: {menu_id}")
    print(f"- Imported products: {imported}")
    print(f"- Backups: {PRODUCTS_BACKUP.name}, {PROFILE_BACKUP.name}")


if __name__ == "__main__":
    main()
