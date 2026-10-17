#!/usr/bin/env python3
"""
Ensure ULTIMATE_PACKAGE is ACTIVE for auth merchants on algoryqrdb.

Uses JWT merchantId (= tbl_merchant.id) as fulfillment user_id (active_package.user_id).
Requires Flyway V150+ (active_package, package_fulfillment, user_entitlement) and tbl_merchant.

Usage:
  python scripts/ensure_merchant_active_package.py trkhamarat@gmail.com
  python scripts/ensure_merchant_active_package.py --check trkhamarat@gmail.com
"""

from __future__ import annotations

import os
import sys
import uuid
from datetime import date, datetime

import psycopg2

PACKAGE_CODE = "ULTIMATE_PACKAGE"
DEFAULT_PERIOD_END = date(2027, 10, 17)


def db_config() -> dict:
    url = os.environ.get("DB_URL", "")
    if url.startswith("jdbc:postgresql://"):
        # jdbc:postgresql://host:port/dbname
        rest = url.removeprefix("jdbc:postgresql://")
        host_port, _, dbname = rest.partition("/")
        host, _, port = host_port.partition(":")
        return dict(
            host=host or "185.184.210.52",
            port=int(port or "5432"),
            dbname=dbname or "algoryqrdb",
            user=os.environ.get("DB_USERNAME", "postgres"),
            password=os.environ.get("DB_PASSWORD", ""),
            sslmode=os.environ.get("DB_SSLMODE", "disable"),
            connect_timeout=15,
        )
    return dict(
        host=os.environ.get("DB_HOST", "185.184.210.52"),
        port=int(os.environ.get("DB_PORT", "5432")),
        dbname=os.environ.get("DB_NAME", "algoryqrdb"),
        user=os.environ.get("DB_USERNAME", "postgres"),
        password=os.environ.get("DB_PASSWORD", ""),
        sslmode=os.environ.get("DB_SSLMODE", "disable"),
        connect_timeout=15,
    )


def require_runtime_schema(cur) -> None:
    cur.execute(
        """
        SELECT
            to_regclass('public.tbl_merchant') IS NOT NULL AS merchant,
            to_regclass('public.active_package') IS NOT NULL AS active_package
        """
    )
    merchant, active_package = cur.fetchone()
    if not merchant or not active_package:
        raise RuntimeError(
            "algoryqrdb is missing auth/fulfillment runtime tables. "
            "Run the application (Flyway) on algoryqrdb first (V143+ / V150+)."
        )


def resolve_merchant_id(cur, email: str) -> int:
    cur.execute(
        """
        SELECT id FROM tbl_merchant
        WHERE lower(trim(email)) = lower(trim(%s))
          AND deleted_at IS NULL
        LIMIT 1
        """,
        (email,),
    )
    row = cur.fetchone()
    if not row:
        raise RuntimeError(f"No tbl_merchant row for email={email}")
    return row[0]


def read_active_package(cur, merchant_id: int) -> dict | None:
    cur.execute(
        """
        SELECT user_id, status, package_code, period_start, period_end
        FROM active_package
        WHERE user_id = %s
        LIMIT 1
        """,
        (merchant_id,),
    )
    row = cur.fetchone()
    if not row:
        return None
    user_id, status, package_code, period_start, period_end = row
    active = (
        status == "ACTIVE"
        and package_code
        and (period_end is None or period_end >= date.today())
    )
    return {
        "merchant_id": merchant_id,
        "user_id": user_id,
        "status": status,
        "package_code": package_code,
        "period_start": period_start,
        "period_end": period_end,
        "active": active,
    }


def load_catalog_package(cur) -> tuple[int, str, str]:
    cur.execute(
        """
        SELECT id, name, currency
        FROM tbl_plan_package
        WHERE code = %s AND active = TRUE
        LIMIT 1
        """,
        (PACKAGE_CODE,),
    )
    row = cur.fetchone()
    if not row:
        raise RuntimeError(f"{PACKAGE_CODE} not found in tbl_plan_package")
    return row[0], row[1], row[2] or "TRY"


def supersede_runtime(cur, merchant_id: int) -> None:
    cur.execute(
        """
        UPDATE package_fulfillment
        SET status = 'SUPERSEDED', updated_at = NOW()
        WHERE user_id = %s AND status = 'ACTIVE'
        """,
        (merchant_id,),
    )
    cur.execute(
        """
        UPDATE user_entitlement
        SET status = 'SUPERSEDED', updated_at = NOW()
        WHERE user_id = %s AND status = 'ACTIVE'
        """,
        (merchant_id,),
    )
    cur.execute("DELETE FROM active_package WHERE user_id = %s", (merchant_id,))


def assign_ultimate(
    cur,
    merchant_id: int,
    package_id: int,
    package_name: str,
    currency: str,
    period_end: date,
) -> dict:
    period_start = date.today()
    starts_at = datetime.now()
    expires_at = datetime.combine(period_end, datetime.max.time())

    cur.execute(
        """
        UPDATE tbl_purchase
        SET status = 'EXPIRED', expires_at = LEAST(COALESCE(expires_at, NOW()), NOW())
        WHERE user_id = %s AND status = 'ACTIVE'
        """,
        (merchant_id,),
    )
    supersede_runtime(cur, merchant_id)

    cur.execute(
        """
        INSERT INTO tbl_purchase (
            user_id, package_id, package_code, package_name,
            price, currency, status, purchase_type, payment_style, payment_mode,
            billing_period, installment_count, system_managed, recurring_consent, refund_status,
            cancel_at_period_end, purchased_at, starts_at, expires_at
        ) VALUES (
            %s, %s, %s, %s,
            0, %s, 'ACTIVE', 'SYSTEM_GRANT', 'ONE_TIME', 'THREE_DS',
            'MONTHLY', 1, TRUE, FALSE, 'NONE',
            FALSE, %s, %s, %s
        )
        RETURNING id
        """,
        (
            merchant_id,
            package_id,
            PACKAGE_CODE,
            package_name,
            currency,
            starts_at,
            starts_at,
            expires_at,
        ),
    )
    purchase_id = cur.fetchone()[0]

    event_id = f"grant:{merchant_id}:{PACKAGE_CODE}:{uuid.uuid4()}"
    cur.execute(
        """
        INSERT INTO package_fulfillment (
            event_id, event_type, purchase_id, user_id, package_id, package_code,
            period_start, period_end, amount, currency, status, created_at, updated_at
        ) VALUES (
            %s, 'package.assigned', %s, %s, %s, %s,
            %s, %s, 0, %s, 'ACTIVE', NOW(), NOW()
        )
        RETURNING id
        """,
        (
            event_id,
            purchase_id,
            merchant_id,
            package_id,
            PACKAGE_CODE,
            period_start,
            period_end,
            currency,
        ),
    )
    fulfillment_id = cur.fetchone()[0]

    cur.execute(
        """
        INSERT INTO active_package (
            user_id, fulfillment_id, purchase_id, package_id, package_code,
            period_start, period_end, status, activated_at, updated_at
        ) VALUES (
            %s, %s, %s, %s, %s,
            %s, %s, 'ACTIVE', NOW(), NOW()
        )
        ON CONFLICT (user_id) DO UPDATE SET
            fulfillment_id = EXCLUDED.fulfillment_id,
            purchase_id = EXCLUDED.purchase_id,
            package_id = EXCLUDED.package_id,
            package_code = EXCLUDED.package_code,
            period_start = EXCLUDED.period_start,
            period_end = EXCLUDED.period_end,
            status = 'ACTIVE',
            updated_at = NOW()
        """,
        (
            merchant_id,
            fulfillment_id,
            purchase_id,
            package_id,
            PACKAGE_CODE,
            period_start,
            period_end,
        ),
    )

    cur.execute(
        """
        SELECT pr.id, pr.code, pr.feature_code, pr.scope_code, pr.type_id,
               ppi.quantity, ppi.unlimited
        FROM tbl_plan_package_item ppi
        JOIN tbl_product pr ON pr.id = ppi.product_id
        WHERE ppi.package_id = %s
        ORDER BY pr.code
        """,
        (package_id,),
    )
    entitlement_count = 0
    ent_start = datetime.combine(period_start, datetime.min.time())
    ent_end = datetime.combine(period_end, datetime.max.time())
    for product_id, product_code, feature_code, scope_code, product_type_id, quantity, unlimited in cur.fetchall():
        feature = (feature_code or "").strip() or product_code
        qty = 0 if unlimited else (quantity or 0)
        cur.execute(
            """
            INSERT INTO user_entitlement (
                user_id, fulfillment_id, purchase_id, product_id, product_type_id,
                feature_code, scope_code, quantity, unlimited, used_quantity,
                source, status, starts_at, expires_at, created_at, updated_at
            ) VALUES (
                %s, %s, %s, %s, %s,
                %s, %s, %s, %s, 0,
                'PACKAGE_INCLUDE', 'ACTIVE', %s, %s, NOW(), NOW()
            )
            """,
            (
                merchant_id,
                fulfillment_id,
                purchase_id,
                product_id,
                product_type_id or "PACKAGE_PRODUCT",
                feature,
                scope_code,
                qty,
                unlimited,
                ent_start,
                ent_end,
            ),
        )
        entitlement_count += 1

    return {
        "merchant_id": merchant_id,
        "purchase_id": purchase_id,
        "fulfillment_id": fulfillment_id,
        "entitlements": entitlement_count,
        "package_code": PACKAGE_CODE,
        "period_end": period_end.isoformat(),
    }


def ensure(email: str, check_only: bool) -> dict:
    conn = psycopg2.connect(**db_config())
    conn.autocommit = False
    cur = conn.cursor()
    try:
        require_runtime_schema(cur)
        merchant_id = resolve_merchant_id(cur, email)
        state = read_active_package(cur, merchant_id)
        if state and state["active"]:
            return {"action": "none", "email": email, **state}
        if check_only:
            return {"action": "missing_or_inactive", "email": email, "merchant_id": merchant_id, "state": state}
        package_id, package_name, currency = load_catalog_package(cur)
        result = assign_ultimate(
            cur, merchant_id, package_id, package_name, currency, DEFAULT_PERIOD_END
        )
        conn.commit()
        verify = read_active_package(cur, merchant_id)
        return {"action": "assigned", "email": email, "assign": result, "verify": verify}
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()
        conn.close()


def main(argv: list[str]) -> None:
    check_only = False
    emails: list[str] = []
    for arg in argv:
        if arg == "--check":
            check_only = True
        elif arg.startswith("-"):
            print(f"Unknown option: {arg}", file=sys.stderr)
            sys.exit(2)
        else:
            emails.append(arg)
    if not emails:
        emails = ["trkhamarat@gmail.com"]
    for email in emails:
        print(ensure(email, check_only))


if __name__ == "__main__":
    main(sys.argv[1:])
