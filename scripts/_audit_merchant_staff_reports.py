#!/usr/bin/env python3
"""Audit staff/reports for legacy tbl_user.id vs tbl_merchant.id alignment."""

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
STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=15,
)

REPORT_TABLES = (
    "tbl_smart_report_events",
    "tbl_smart_report_results",
    "tbl_batch_reports",
    "tbl_batch_report_items",
    "tbl_user_accounting_entry",
    "tbl_work_shift",
)


def legacy_map(cur):
    cur.execute(
        """
        SELECT u.id, m.id, u.email
        FROM tbl_user u
        JOIN tbl_merchant m ON lower(trim(m.email)) = lower(trim(u.email))
        WHERE u.deleted_at IS NULL AND m.deleted_at IS NULL AND u.id <> m.id
        """
    )
    return cur.fetchall()


def audit(label: str, cfg: dict) -> None:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    print(f"=== {label} ===")
    maps = legacy_map(cur)
    print("id mismatches:", len(maps))
    for row in maps[:10]:
        print(" ", row)
    legacy_ids = [r[0] for r in maps]

    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_merchant_staff staff
        JOIN (
            SELECT u.id AS legacy_id, m.id AS merchant_id
            FROM tbl_user u
            JOIN tbl_merchant m ON lower(trim(m.email)) = lower(trim(u.email))
            WHERE u.deleted_at IS NULL AND m.deleted_at IS NULL
        ) map ON staff.merchant_id = map.legacy_id AND map.legacy_id <> map.merchant_id
        """
    )
    print("staff still on legacy user id:", cur.fetchone()[0])

    cur.execute("SELECT COUNT(*) FROM tbl_merchant_staff")
    print("total staff:", cur.fetchone()[0])
    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_merchant_staff s
        WHERE NOT EXISTS (
            SELECT 1 FROM tbl_merchant m
            WHERE m.id = s.merchant_id AND m.deleted_at IS NULL
        )
        """
    )
    print("staff with orphan merchant_id:", cur.fetchone()[0])
    cur.execute(
        """
        SELECT s.id, s.merchant_id, s.username, s.active
        FROM tbl_merchant_staff s
        WHERE NOT EXISTS (
            SELECT 1 FROM tbl_merchant m
            WHERE m.id = s.merchant_id AND m.deleted_at IS NULL
        )
        ORDER BY s.merchant_id, s.id
        LIMIT 15
        """
    )
    for r in cur.fetchall():
        print("  orphan", r)

    if legacy_ids:
        cur.execute("SELECT COUNT(*) FROM tbl_menu WHERE user_id = ANY(%s)", (legacy_ids,))
        print("menus on legacy user_id:", cur.fetchone()[0])

    for t in REPORT_TABLES:
        cur.execute("SELECT to_regclass(%s)", (f"public.{t}",))
        if not cur.fetchone()[0]:
            continue
        cur.execute(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = %s
              AND column_name IN ('user_id', 'merchant_id', 'used_by_user_id')
            """,
            (t,),
        )
        for (col,) in cur.fetchall():
            if legacy_ids:
                cur.execute(f"SELECT COUNT(*) FROM {t} WHERE {col} = ANY(%s)", (legacy_ids,))
                n = cur.fetchone()[0]
                if n:
                    print(f"  {t}.{col} on legacy ids: {n}")

    cur.execute(
        """
        SELECT table_name FROM information_schema.columns
        WHERE table_schema = 'public' AND column_name = 'owner_user_id'
        ORDER BY table_name
        """
    )
    owner_cols = [r[0] for r in cur.fetchall()]
    print("tables still using owner_user_id:", owner_cols)

    cur.execute("SELECT to_regclass('public.tbl_menu_waiter')")
    print("tbl_menu_waiter exists:", cur.fetchone()[0] is not None)

    for email in ("tavsancoffe@gmail.com", "trkhamarat@gmail.com"):
        cur.execute(
            """
            SELECT m.id FROM tbl_merchant m
            WHERE lower(trim(m.email)) = lower(trim(%s)) AND m.deleted_at IS NULL
            """,
            (email,),
        )
        mid = cur.fetchone()
        if not mid:
            print(f"{email}: no merchant")
            continue
        mid = mid[0]
        cur.execute(
            "SELECT COUNT(*) FROM tbl_merchant_staff WHERE merchant_id = %s",
            (mid,),
        )
        sc = cur.fetchone()[0]
        cur.execute(
            "SELECT COUNT(*) FROM tbl_menu WHERE user_id = %s AND COALESCE(is_deleted, false) = false",
            (mid,),
        )
        mc = cur.fetchone()[0]
        cur.execute(
            """
            SELECT COUNT(*) FROM tbl_smart_report_events e
            JOIN tbl_menu menu ON menu.menu_id = e.menu_id
            WHERE menu.user_id = %s
            """,
            (mid,),
        )
        rc = cur.fetchone()[0] if cur.rowcount is not None else 0
        try:
            cur.execute(
                """
                SELECT COUNT(*) FROM tbl_smart_report_events e
                JOIN tbl_menu menu ON menu.menu_id = e.menu_id
                WHERE menu.user_id = %s
                """,
                (mid,),
            )
            rc = cur.fetchone()[0]
        except Exception:
            conn.rollback()
            rc = "n/a"
        print(f"{email} merchant={mid} staff={sc} menus={mc} smart_report_events(via menu)={rc}")

    conn.close()


if __name__ == "__main__":
    audit("PROD", PROD)
    print()
    audit("STAGE", STAGE)
