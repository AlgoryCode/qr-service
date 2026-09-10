import os
import sys

import psycopg2
from psycopg2.extras import RealDictCursor

PACKAGE_CODES = (
    "STARTER_PACKAGE",
    "PRO_PACKAGE",
    "ULTIMATE_PACKAGE",
    "ULTIMATE_TRIAL_PACKAGE",
)


def connect():
    host = os.environ.get("DB_HOST")
    port = int(os.environ.get("DB_PORT", "5432"))
    dbname = os.environ.get("DB_NAME")
    user = os.environ.get("DB_USERNAME", "postgres")
    password = os.environ.get("DB_PASSWORD")
    if not host or not dbname or not password:
        raise SystemExit("DB_HOST, DB_NAME and DB_PASSWORD are required")
    return psycopg2.connect(
        host=host,
        port=port,
        dbname=dbname,
        user=user,
        password=password,
        sslmode=os.environ.get("DB_SSLMODE", "disable"),
        connect_timeout=10,
    )


def main() -> None:
    conn = connect()
    conn.autocommit = False
    cur = conn.cursor(cursor_factory=RealDictCursor)

    cur.execute(
        """
        INSERT INTO tbl_product (
            code, name, description, active, scope_code, type_id, feature_code,
            consumable, addon_purchasable, requires_count_sync, unit_price, vat_rate,
            created_at, updated_at
        )
        SELECT
            'SMART_REPORTING_ADDON',
            'Ek Akilli Rapor',
            'Haftalik ucretsiz hak disinda ek akilli rapor',
            TRUE,
            'SMART_REPORTING_OWNER',
            'ADDON_PRODUCT',
            'SMART_REPORTING',
            TRUE,
            TRUE,
            FALSE,
            200.00,
            20.00,
            NOW(),
            NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM tbl_product WHERE code = 'SMART_REPORTING_ADDON'
        )
        """
    )

    cur.execute(
        """
        UPDATE tbl_product SET
            name = 'Ek Akilli Rapor',
            description = 'Haftalik ucretsiz hak disinda ek akilli rapor',
            scope_code = 'SMART_REPORTING_OWNER',
            type_id = 'ADDON_PRODUCT',
            feature_code = 'SMART_REPORTING',
            consumable = TRUE,
            addon_purchasable = TRUE,
            requires_count_sync = FALSE,
            unit_price = 200.00,
            vat_rate = 20.00,
            active = TRUE,
            updated_at = NOW()
        WHERE code = 'SMART_REPORTING_ADDON'
        """
    )

    cur.execute(
        """
        UPDATE tbl_product SET
            feature_code = COALESCE(feature_code, 'SMART_REPORTING'),
            scope_code = 'SMART_REPORTING_OWNER',
            consumable = FALSE,
            addon_purchasable = FALSE,
            active = TRUE,
            updated_at = NOW()
        WHERE code = 'SMART_REPORTING'
        """
    )

    cur.execute("SELECT id FROM tbl_product WHERE code = 'SMART_REPORTING_ADDON'")
    product = cur.fetchone()
    if product is None:
        raise RuntimeError("SMART_REPORTING_ADDON missing after upsert")
    product_id = product["id"]

    cur.execute(
        "SELECT id, code FROM tbl_plan_package WHERE code = ANY(%s)",
        (list(PACKAGE_CODES),),
    )
    packages = cur.fetchall()
    for package in packages:
        cur.execute(
            """
            INSERT INTO tbl_plan_package_addon (package_id, product_id, active, created_at)
            SELECT %s, %s, TRUE, NOW()
            WHERE NOT EXISTS (
                SELECT 1 FROM tbl_plan_package_addon
                WHERE package_id = %s AND product_id = %s
            )
            """,
            (package["id"], product_id, package["id"], product_id),
        )
        cur.execute(
            """
            UPDATE tbl_plan_package_addon
            SET active = TRUE
            WHERE package_id = %s AND product_id = %s
            """,
            (package["id"], product_id),
        )

    conn.commit()

    cur.execute(
        """
        SELECT code, name, type_id, feature_code, consumable, addon_purchasable,
               unit_price, vat_rate, active
        FROM tbl_product WHERE code = 'SMART_REPORTING_ADDON'
        """
    )
    print("product:", dict(cur.fetchone()))
    cur.execute(
        """
        SELECT p.code AS package_code, a.active
        FROM tbl_plan_package_addon a
        JOIN tbl_plan_package p ON p.id = a.package_id
        WHERE a.product_id = %s
        ORDER BY p.code
        """,
        (product_id,),
    )
    print("links:")
    for row in cur.fetchall():
        print(dict(row))

    cur.close()
    conn.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("FAILED:", exc, file=sys.stderr)
        raise
