from __future__ import annotations

import json
import urllib.error
import urllib.request

import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
    connect_timeout=15,
)
USER_ID = 25
PUBLIC_ID = "yNFfDXy1hJlCjHZKVh6SIw"


def fetch_one(cur, sql: str, params: tuple = ()) -> tuple | None:
    cur.execute(sql, params)
    return cur.fetchone()


def main() -> None:
    conn = psycopg2.connect(**PROD)
    conn.autocommit = False
    cur = conn.cursor()
    try:
        cur.execute(
            """
            SELECT menu_id, public_access_enabled, public_access_disabled_reason, public_id
            FROM tbl_menu
            WHERE user_id = %s AND is_deleted = false
            """,
            (USER_ID,),
        )
        print("menus before", cur.fetchall())
        cur.execute(
            """
            SELECT id, status, purchase_type, package_code, starts_at, expires_at
            FROM tbl_purchase
            WHERE user_id = %s
            ORDER BY id
            """,
            (USER_ID,),
        )
        print("purchases before", cur.fetchall())
        trial = fetch_one(
            cur,
            """
            SELECT id, status, package_id, package_code, started_at, ends_at
            FROM tbl_trial_log
            WHERE user_id = %s
            """,
            (USER_ID,),
        )
        print("trial", trial)
        if trial is None or trial[1] != "ACTIVE":
            raise RuntimeError("Aktif trial yok")
        trial_id, _, package_id, package_code, started_at, ends_at = trial
        pkg = fetch_one(
            cur,
            "SELECT id, code, name FROM tbl_plan_package WHERE id = %s",
            (package_id,),
        )
        if pkg is None:
            raise RuntimeError("Paket yok")
        existing = fetch_one(
            cur,
            """
            SELECT id FROM tbl_purchase
            WHERE user_id = %s AND status = 'ACTIVE'
              AND purchase_type IN ('FREE', 'SYSTEM_GRANT')
              AND package_code = %s
            ORDER BY id DESC LIMIT 1
            """,
            (USER_ID, package_code),
        )
        if existing:
            purchase_id = existing[0]
            cur.execute(
                """
                UPDATE tbl_purchase
                SET starts_at = %s, expires_at = %s, status = 'ACTIVE'
                WHERE id = %s
                """,
                (started_at, ends_at, purchase_id),
            )
            print("updated purchase", purchase_id)
        else:
            cur.execute(
                """
                INSERT INTO tbl_purchase (
                    user_id, package_id, package_code, package_name,
                    status, purchase_type, payment_mode, payment_style,
                    price, currency, billing_period,
                    purchased_at, starts_at, expires_at,
                    installment_count, system_managed, recurring_consent, refund_status,
                    cancel_at_period_end
                ) VALUES (
                    %s, %s, %s, %s,
                    'ACTIVE', 'FREE', 'THREE_DS', 'ONE_TIME',
                    0.00, 'TRY', 'MONTHLY',
                    %s, %s, %s,
                    1, false, false, 'NONE',
                    false
                ) RETURNING id
                """,
                (USER_ID, pkg[0], pkg[1], pkg[2], started_at, started_at, ends_at),
            )
            purchase_id = cur.fetchone()[0]
            print("created purchase", purchase_id)
        cur.execute(
            """
            UPDATE tbl_menu
            SET public_access_enabled = true,
                public_access_disabled_reason = NULL,
                updated_at = NOW()
            WHERE user_id = %s AND is_deleted = false
            RETURNING menu_id, public_id, public_access_enabled, public_access_disabled_reason
            """,
            (USER_ID,),
        )
        print("menus after", cur.fetchall())
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    url = f"https://prod.qrapi.algorycode.com/menu/public/{PUBLIC_ID}"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print("api", resp.status, data.get("menu", {}).get("businessName"), data.get("productTotalElements"))
    except urllib.error.HTTPError as exc:
        print("api fail", exc.code, exc.read().decode("utf-8", "replace"))


if __name__ == "__main__":
    main()
