#!/usr/bin/env python3
"""List public columns that still use merchant-owner naming (user_id / used_by_user_id)."""

from __future__ import annotations

import argparse

import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=30,
)
PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
    connect_timeout=30,
)

EXCLUDED = (
    "tbl_user",
    "tbl_customer",
    "tbl_admin",
    "tbl_dashboard_user",
    "tbl_merchant",
)

SPECIAL_RENAMES = {
    ("merchants", "user_id"): "owner_merchant_id",
    ("tbl_coupon", "used_by_user_id"): "used_by_merchant_id",
}


def list_columns(cfg: dict, label: str) -> None:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND column_name IN ('user_id', 'used_by_user_id', 'merchant_id', 'owner_merchant_id', 'used_by_merchant_id')
          AND table_name <> ALL(%s)
        ORDER BY table_name, column_name
        """,
        (list(EXCLUDED),),
    )
    rows = cur.fetchall()
    print(f"=== {label} ({len(rows)} columns) ===")
    for table, column in rows:
        target = SPECIAL_RENAMES.get((table, column), "merchant_id" if column == "user_id" else column)
        if column in ("merchant_id", "owner_merchant_id", "used_by_merchant_id"):
            print(f"  OK  {table}.{column}")
        else:
            print(f"  TODO {table}.{column} -> {target}")
    conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env", choices=("stage", "prod", "both"), default="stage")
    args = parser.parse_args()
    if args.env in ("prod", "both"):
        list_columns(PROD, "PROD")
    if args.env in ("stage", "both"):
        list_columns(STAGE, "STAGE")


if __name__ == "__main__":
    main()
