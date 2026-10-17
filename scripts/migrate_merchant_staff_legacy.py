#!/usr/bin/env python3
"""
Repoint tbl_merchant_staff.merchant_id from legacy tbl_user.id to tbl_merchant.id (V156 logic).
Optionally sync staff rows from prod to stage for merchants matched by email.
"""

from __future__ import annotations

import argparse
import sys

import psycopg2
from psycopg2.extras import execute_values

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
    connect_timeout=30,
)
STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=30,
)

REPOINT_STAFF_SQL = """
UPDATE tbl_merchant_staff staff
SET merchant_id = map.merchant_id
FROM (
    SELECT m.id AS merchant_id, u.id AS legacy_user_id
    FROM tbl_merchant m
    INNER JOIN tbl_user u ON lower(trim(u.email)) = lower(trim(m.email))
    WHERE u.deleted_at IS NULL
      AND m.deleted_at IS NULL
      AND m.id <> u.id
) map
WHERE staff.merchant_id = map.legacy_user_id;
"""

COPY_COLUMNS = [
    "branch_id",
    "username",
    "password_hash",
    "display_name",
    "active",
    "staff_role",
    "role_id",
    "commission_type",
    "commission_rate",
    "commission_fixed_amount",
    "menu_id",
    "phone",
    "pin_hash",
    "created_at",
    "updated_at",
]


def repoint(cfg: dict, label: str, *, dry_run: bool) -> int:
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()
    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_merchant_staff staff
        JOIN (
            SELECT u.id AS legacy_id, m.id AS merchant_id
            FROM tbl_user u
            JOIN tbl_merchant m ON lower(trim(m.email)) = lower(trim(u.email))
            WHERE u.deleted_at IS NULL AND m.deleted_at IS NULL AND u.id <> m.id
        ) map ON staff.merchant_id = map.legacy_id
        """
    )
    pending = int(cur.fetchone()[0])
    print(f"[{label}] staff rows on legacy user id (before): {pending}")
    if dry_run or pending == 0:
        conn.close()
        return pending
    cur.execute(REPOINT_STAFF_SQL)
    updated = cur.rowcount
    conn.commit()
    print(f"[{label}] repointed staff rows: {updated}")
    conn.close()
    return updated


def table_columns(cur, table: str) -> set[str]:
    cur.execute(
        """
        SELECT column_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = %s
        """,
        (table,),
    )
    return {r[0] for r in cur.fetchall()}


def sync_staff_prod_to_stage(*, dry_run: bool, emails: list[str] | None) -> None:
    prod = psycopg2.connect(**PROD)
    stage = psycopg2.connect(**STAGE)
    prod.autocommit = True
    stage.autocommit = False
    pcur = prod.cursor()
    scur = stage.cursor()

    email_filter = ""
    params: tuple = ()
    if emails:
        email_filter = "AND lower(trim(m.email)) = ANY(%s)"
        params = ([e.lower().strip() for e in emails],)

    if emails:
        pcur.execute(
            f"""
            SELECT lower(trim(m.email)) AS email, m.id AS prod_merchant_id
            FROM tbl_merchant m
            WHERE m.deleted_at IS NULL {email_filter}
            ORDER BY m.id
            """,
            params,
        )
    else:
        pcur.execute(
            """
            SELECT lower(trim(m.email)) AS email, m.id AS prod_merchant_id
            FROM tbl_merchant m
            WHERE m.deleted_at IS NULL
            ORDER BY m.id
            """
        )
    merchants = pcur.fetchall()

    stage_cols = table_columns(scur, "tbl_merchant_staff")
    use_cols = [c for c in COPY_COLUMNS if c in stage_cols]

    inserted_total = 0
    for email, prod_mid in merchants:
        scur.execute(
            """
            SELECT id FROM tbl_merchant m
            WHERE lower(trim(m.email)) = %s AND m.deleted_at IS NULL
            """,
            (email,),
        )
        stage_row = scur.fetchone()
        if not stage_row:
            print(f"[sync] skip {email}: no stage merchant")
            continue
        stage_mid = stage_row[0]

        pcur.execute(
            f"SELECT {', '.join(use_cols)} FROM tbl_merchant_staff WHERE merchant_id = %s ORDER BY id",
            (prod_mid,),
        )
        prod_staff = pcur.fetchall()
        if not prod_staff:
            continue

        scur.execute(
            "SELECT lower(username) FROM tbl_merchant_staff WHERE merchant_id = %s",
            (stage_mid,),
        )
        existing = {r[0] for r in scur.fetchall()}
        to_insert = []
        for row in prod_staff:
            username = row[use_cols.index("username")].lower()
            if username in existing:
                continue
            to_insert.append((stage_mid, *row))

        if not to_insert:
            print(f"[sync] {email}: stage merchant {stage_mid} already has {len(existing)} usernames")
            continue

        print(
            f"[sync] {email}: prod merchant {prod_mid} -> stage {stage_mid}, "
            f"insert {len(to_insert)} staff (prod has {len(prod_staff)})"
        )
        if dry_run:
            continue

        sql = (
            f"INSERT INTO tbl_merchant_staff (merchant_id, {', '.join(use_cols)}) "
            f"VALUES %s"
        )
        execute_values(scur, sql, to_insert)
        inserted_total += len(to_insert)

    if dry_run:
        stage.rollback()
    else:
        stage.commit()
    print(f"[sync] inserted on stage: {inserted_total}")
    prod.close()
    stage.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env", choices=("prod", "stage", "both"), default="both")
    parser.add_argument("--sync-stage-from-prod", action="store_true")
    parser.add_argument("--email", action="append", help="Limit sync to merchant email(s)")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    targets = []
    if args.env in ("prod", "both"):
        targets.append(("PROD", PROD))
    if args.env in ("stage", "both"):
        targets.append(("STAGE", STAGE))
    for label, cfg in targets:
        repoint(cfg, label, dry_run=args.dry_run)

    if args.sync_stage_from_prod:
        sync_staff_prod_to_stage(dry_run=args.dry_run, emails=args.email)


if __name__ == "__main__":
    main()
