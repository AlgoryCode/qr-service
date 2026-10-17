#!/usr/bin/env python3
"""Backfill tbl_merchant from tbl_user and repoint legacy user_id (V157/V159 logic)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[1]
V159 = ROOT / "src/main/resources/db/migration/V159__align_all_tbl_user_to_merchant_id.sql"

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


def count_missing(cur) -> int:
    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_user u
        WHERE u.deleted_at IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM tbl_merchant m
            WHERE m.deleted_at IS NULL
              AND (m.id = u.id OR lower(trim(m.email)) = lower(trim(u.email)))
          )
        """
    )
    return int(cur.fetchone()[0])


def count_id_mismatch(cur) -> int:
    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_user u
        INNER JOIN tbl_merchant m ON lower(trim(m.email)) = lower(trim(u.email))
        WHERE u.deleted_at IS NULL AND m.deleted_at IS NULL AND u.id <> m.id
        """
    )
    return int(cur.fetchone()[0])


def run_backfill(label: str, cfg: dict, *, dry_run: bool) -> None:
    sql = V159.read_text(encoding="utf-8")
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()
    before_missing = count_missing(cur)
    before_mismatch = count_id_mismatch(cur)
    print(f"[{label}] before: missing_merchant={before_missing} id_mismatch={before_mismatch}")
    if dry_run:
        conn.close()
        return
    try:
        cur.execute(sql)
        conn.commit()
    except Exception as exc:
        conn.rollback()
        print(f"[{label}] FAILED: {exc}", file=sys.stderr)
        conn.close()
        raise SystemExit(1) from exc
    after_missing = count_missing(cur)
    after_mismatch = count_id_mismatch(cur)
    print(f"[{label}] after: missing_merchant={after_missing} id_mismatch={after_mismatch}")
    conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env", choices=("prod", "stage", "both"), default="both")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    targets = []
    if args.env in ("prod", "both"):
        targets.append(("PROD", PROD))
    if args.env in ("stage", "both"):
        targets.append(("STAGE", STAGE))
    for label, cfg in targets:
        run_backfill(label, cfg, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
