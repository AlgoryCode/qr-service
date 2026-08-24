#!/usr/bin/env python3
from __future__ import annotations

import os

import psycopg2

LEGACY_COLUMNS = ("public_slug", "url_mode")


def connect() -> psycopg2.extensions.connection:
    return psycopg2.connect(
        host=os.environ.get("DB_HOST", "185.184.210.52"),
        port=os.environ.get("DB_PORT", "5432"),
        dbname=os.environ.get("DB_NAME", "algoryqrdb"),
        user=os.environ.get("DB_USERNAME", "postgres"),
        password=os.environ["DB_PASSWORD"],
    )


def main() -> None:
    conn = connect()
    conn.autocommit = True
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'tbl_menu'
              AND column_name = ANY(%s)
            ORDER BY column_name
            """,
            (list(LEGACY_COLUMNS),),
        )
        existing = [row[0] for row in cur.fetchall()]
        if not existing:
            print("No legacy tbl_menu columns found.")
            return
        for column in existing:
            cur.execute(f"ALTER TABLE tbl_menu DROP COLUMN IF EXISTS {column}")
            print(f"Dropped tbl_menu.{column}")
    conn.close()


if __name__ == "__main__":
    main()
