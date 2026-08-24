#!/usr/bin/env python3
from __future__ import annotations

import os
from pathlib import Path

import psycopg2

MIGRATION_TABLE = "tbl_schema_migration"
MIGRATION_ID = "001_addon_purchase.sql"
MIGRATION_FILE = (
    Path(__file__).resolve().parents[1]
    / "src"
    / "main"
    / "resources"
    / "schema"
    / MIGRATION_ID
)


def connect() -> psycopg2.extensions.connection:
    return psycopg2.connect(
        host=os.environ.get("DB_HOST", "185.184.210.52"),
        port=os.environ.get("DB_PORT", "5433"),
        dbname=os.environ.get("DB_NAME", "algoryqrdb-stage"),
        user=os.environ.get("DB_USERNAME", "postgres"),
        password=os.environ["DB_PASSWORD"],
    )


def main() -> None:
    sql = MIGRATION_FILE.read_text(encoding="utf-8")
    conn = connect()
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            cur.execute(
                f"""
                CREATE TABLE IF NOT EXISTS {MIGRATION_TABLE} (
                    id VARCHAR(128) PRIMARY KEY,
                    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """
            )
            cur.execute(
                f"SELECT EXISTS (SELECT 1 FROM {MIGRATION_TABLE} WHERE id = %s)",
                (MIGRATION_ID,),
            )
            if cur.fetchone()[0]:
                print(f"Migration already applied: {MIGRATION_ID}")
                conn.rollback()
                return
            cur.execute(sql)
            cur.execute(
                f"INSERT INTO {MIGRATION_TABLE} (id) VALUES (%s)",
                (MIGRATION_ID,),
            )
        conn.commit()
        print(f"Applied migration: {MIGRATION_ID}")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
