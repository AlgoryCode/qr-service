import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=10,
)

EMAIL = "trkhamarat@gmail.com"
CREDITS = 300
PRODUCT_CODE = "SMART_REPORTING_ADDON"
FEATURE_CODE = "SMART_REPORTING"
SCOPE_CODE = "SMART_REPORTING_OWNER"


def main() -> None:
    conn = psycopg2.connect(**STAGE)
    conn.autocommit = False
    cur = conn.cursor()

    cur.execute("SELECT id, email FROM tbl_user WHERE email = %s", (EMAIL,))
    user = cur.fetchone()
    if user is None:
        raise SystemExit(f"user not found: {EMAIL}")
    user_id = user[0]
    print("user_id", user_id)

    cur.execute(
        """
        SELECT id, unit_price, scope_code
        FROM tbl_product
        WHERE code = %s
        """,
        (PRODUCT_CODE,),
    )
    product = cur.fetchone()
    if product is None:
        raise SystemExit(f"product missing: {PRODUCT_CODE}")
    product_id, unit_price, scope_code = product
    scope = scope_code or SCOPE_CODE
    print("product_id", product_id, "unit_price", unit_price)

    cur.execute(
        """
        SELECT f.id, d.id, d.quantity, d.used_quantity, d.expires_at
        FROM tbl_fulfillment_detail d
        JOIN tbl_fulfillment f ON f.id = d.fulfillment_id
        WHERE d.user_id = %s
          AND d.feature_code = %s
          AND d.source = 'ADDON_PURCHASE'
          AND f.status = 'ACTIVE'
          AND (d.expires_at IS NULL OR d.expires_at > NOW())
        ORDER BY d.id DESC
        """,
        (user_id, FEATURE_CODE),
    )
    existing = cur.fetchall()
    print("existing_active_addon_details", existing)

    if existing:
        detail_id = existing[0][1]
        cur.execute(
            """
            UPDATE tbl_fulfillment_detail
            SET quantity = %s,
                used_quantity = 0,
                starts_at = NOW(),
                expires_at = NOW() + INTERVAL '1 year',
                version = version + 1
            WHERE id = %s
            RETURNING id, quantity, used_quantity, expires_at
            """,
            (CREDITS, detail_id),
        )
        print("updated_detail", cur.fetchone())
        cur.execute(
            """
            UPDATE tbl_fulfillment
            SET starts_at = NOW(),
                expires_at = NOW() + INTERVAL '1 year'
            WHERE id = %s
            """,
            (existing[0][0],),
        )
        print("updated_fulfillment", existing[0][0])
    else:
        cur.execute(
            """
            SELECT package_id, package_code, package_name
            FROM tbl_purchase
            WHERE user_id = %s AND status = 'ACTIVE' AND purchase_type = 'PAID'
            ORDER BY id DESC
            LIMIT 1
            """,
            (user_id,),
        )
        base = cur.fetchone()
        if base is None:
            cur.execute(
                """
                SELECT id, code, name
                FROM tbl_plan_package
                WHERE id = (
                    SELECT package_id FROM tbl_fulfillment
                    WHERE user_id = %s AND status = 'ACTIVE'
                    ORDER BY id DESC LIMIT 1
                )
                """,
                (user_id,),
            )
            pkg = cur.fetchone()
            if pkg is None:
                raise SystemExit("no active package for user")
            package_id, package_code, package_name = pkg
        else:
            package_id, package_code, package_name = base
        print("package", package_id, package_code, package_name)

        price = (unit_price or 0) * CREDITS
        cur.execute(
            """
            INSERT INTO tbl_purchase (
                user_id, currency, package_code, package_id, package_name,
                price, purchased_at, starts_at, expires_at, status,
                billing_period, cancel_at_period_end, installment_count,
                payment_mode, payment_style, purchase_type, refund_status,
                system_managed, product_id, addon_quantity, recurring_consent
            )
            VALUES (
                %s, 'TRY', %s, %s, %s,
                %s, NOW(), NOW(), NOW() + INTERVAL '1 year', 'ACTIVE',
                'MONTHLY', FALSE, 1,
                'CHECKOUT_FORM', 'ONE_TIME', 'ADD_ON', 'NONE',
                TRUE, %s, %s, FALSE
            )
            RETURNING id
            """,
            (
                user_id,
                PRODUCT_CODE,
                package_id,
                "Ek Akilli Rapor",
                price,
                product_id,
                CREDITS,
            ),
        )
        purchase_id = cur.fetchone()[0]
        print("purchase_id", purchase_id)

        cur.execute(
            """
            INSERT INTO tbl_fulfillment (
                user_id, purchase_id, package_id, status,
                starts_at, expires_at, created_at
            )
            VALUES (
                %s, %s, %s, 'ACTIVE',
                NOW(), NOW() + INTERVAL '1 year', NOW()
            )
            RETURNING id
            """,
            (user_id, purchase_id, package_id),
        )
        fulfillment_id = cur.fetchone()[0]
        print("fulfillment_id", fulfillment_id)

        cur.execute(
            """
            INSERT INTO tbl_fulfillment_detail (
                fulfillment_id, user_id, product_id, product_type_id,
                feature_code, scope_code, quantity, unlimited, used_quantity,
                source, starts_at, expires_at, version, created_at
            )
            VALUES (
                %s, %s, %s, 'ADDON_PRODUCT',
                %s, %s, %s, FALSE, 0,
                'ADDON_PURCHASE', NOW(), NOW() + INTERVAL '1 year', 0, NOW()
            )
            RETURNING id, quantity, used_quantity
            """,
            (
                fulfillment_id,
                user_id,
                product_id,
                FEATURE_CODE,
                scope,
                CREDITS,
            ),
        )
        print("created_detail", cur.fetchone())

    conn.commit()

    cur.execute(
        """
        SELECT d.id, d.quantity, d.used_quantity,
               (d.quantity - d.used_quantity) AS remaining,
               d.source, d.starts_at, d.expires_at, f.status
        FROM tbl_fulfillment_detail d
        JOIN tbl_fulfillment f ON f.id = d.fulfillment_id
        WHERE d.user_id = %s
          AND d.feature_code = %s
          AND d.source = 'ADDON_PURCHASE'
          AND f.status = 'ACTIVE'
        ORDER BY d.id DESC
        """,
        (user_id, FEATURE_CODE),
    )
    print("final_paid_credits")
    for row in cur.fetchall():
        print(" ", row)

    cur.close()
    conn.close()
    print("DONE")


if __name__ == "__main__":
    main()
