#!/usr/bin/env python3
"""Audit, apply, and validate tbl_user -> tbl_merchant cutover (Flyway V156-V160)."""

from __future__ import annotations

import argparse
import sys
import zlib
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / "src/main/resources/db/migration"

CUTover_VERSIONS = (
    ("156", "align panel data to auth merchant id", "V156__align_panel_data_to_auth_merchant_id.sql"),
    ("157", "auth backfill merchants from users", "V157__auth_backfill_merchants_from_users.sql"),
    ("158", "align purchase user to merchant id", "V158__align_purchase_user_to_merchant_id.sql"),
    ("159", "align all tbl user to merchant id", "V159__align_all_tbl_user_to_merchant_id.sql"),
)

RENAME_VERSION = (
    "160",
    "rename merchant owner user id to merchant id",
    "V160__rename_merchant_owner_user_id_to_merchant_id.sql",
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
STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=30,
)

MAP_SUBQUERY = """
    SELECT u.id AS legacy_user_id, m.id AS merchant_id
    FROM tbl_user u
    JOIN tbl_merchant m ON lower(trim(m.email)) = lower(trim(u.email))
    WHERE u.deleted_at IS NULL AND m.deleted_at IS NULL AND u.id <> m.id
"""

# Panel rows still keyed by legacy tbl_user.id on the merchant-owner column.
LEGACY_FK_OWNER_TABLES: tuple[tuple[str, str], ...] = (
    ("tbl_menu", "merchant_id"),
    ("tbl_branch", "merchant_id"),
    ("tbl_purchase", "merchant_id"),
    ("merchants", "owner_merchant_id"),
    ("tbl_menu_category", "merchant_id"),
    ("tbl_menu_products", "merchant_id"),
    ("tbl_menu_product_option_group", "merchant_id"),
    ("tbl_smart_report_events", "merchant_id"),
    ("tbl_batch_reports", "merchant_id"),
    ("tbl_user_accounting_entry", "merchant_id"),
    ("tbl_work_shift", "merchant_id"),
    ("user_entitlement", "merchant_id"),
    ("package_fulfillment", "merchant_id"),
)


def merchant_owner_column(cur, table: str, preferred: str = "merchant_id") -> str | None:
    """Return owner FK column: preferred name, legacy user_id, or store owner_merchant_id."""
    for column in (preferred, "merchant_id", "owner_merchant_id", "user_id"):
        cur.execute(
            """
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = %s AND column_name = %s
            LIMIT 1
            """,
            (table, column),
        )
        if cur.fetchone():
            return column
    return None

SNAPSHOT_SAMPLE_EMAILS = (
    "tavsancoffe@gmail.com",
    "trkhamarat@gmail.com",
)


def flyway_applied(cur, version: str) -> bool:
    cur.execute(
        "SELECT 1 FROM flyway_schema_history WHERE version = %s AND success = TRUE LIMIT 1",
        (version,),
    )
    return cur.fetchone() is not None


def checksum_sql(path: Path) -> int:
    """Match Flyway CRC32 checksum for SQL migrations."""
    value = zlib.crc32(path.read_bytes()) & 0xFFFFFFFF
    if value >= 2**31:
        value -= 2**32
    return value


def record_flyway(cur, version: str, description: str, script: str, path: Path) -> None:
    cur.execute("SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history")
    rank = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO flyway_schema_history (
            installed_rank, version, description, type, script, checksum,
            installed_by, installed_on, execution_time, success
        ) VALUES (%s, %s, %s, 'SQL', %s, %s, 'user_merchant_cutover', NOW(), 0, TRUE)
        """,
        (rank, version, description, script, checksum_sql(path)),
    )


def audit(cur) -> dict:
    metrics: dict = {}

    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_user u
        JOIN tbl_merchant m ON lower(trim(m.email)) = lower(trim(u.email))
        WHERE u.deleted_at IS NULL AND m.deleted_at IS NULL AND u.id <> m.id
        """
    )
    metrics["email_id_mismatch_pairs"] = int(cur.fetchone()[0])

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
    metrics["live_users_without_merchant"] = int(cur.fetchone()[0])

    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_user u
        JOIN tbl_merchant m ON u.phone IS NOT NULL AND m.phone = u.phone
        WHERE u.deleted_at IS NULL AND m.deleted_at IS NULL
          AND lower(trim(u.email)) <> lower(trim(m.email))
        """
    )
    metrics["phone_conflicts"] = int(cur.fetchone()[0])

    cur.execute(
        """
        SELECT COUNT(*) FROM tbl_user u
        JOIN tbl_merchant m ON u.provider_subject IS NOT NULL AND m.provider_subject = u.provider_subject
        WHERE u.deleted_at IS NULL AND m.deleted_at IS NULL
          AND lower(trim(u.email)) <> lower(trim(m.email))
        """
    )
    metrics["provider_subject_conflicts"] = int(cur.fetchone()[0])

    active_pkg_col = merchant_owner_column(cur, "active_package") or "merchant_id"
    cur.execute(
        f"""
        SELECT COUNT(*) FROM (
            {MAP_SUBQUERY}
        ) map
        WHERE EXISTS (
            SELECT 1 FROM active_package ap
            WHERE ap.{active_pkg_col} = map.legacy_user_id
        )
          AND EXISTS (
            SELECT 1 FROM active_package ap
            WHERE ap.{active_pkg_col} = map.merchant_id
        )
        """
    )
    metrics["double_active_package_pairs"] = int(cur.fetchone()[0])

    for table, preferred_col in LEGACY_FK_OWNER_TABLES:
        cur.execute("SELECT to_regclass(%s)", (f"public.{table}",))
        if not cur.fetchone()[0]:
            metrics[f"legacy_fk_{table}"] = 0
            continue
        owner_col = merchant_owner_column(cur, table, preferred_col)
        if not owner_col:
            metrics[f"legacy_fk_{table}"] = 0
            continue
        cur.execute(
            f"""
            SELECT COUNT(*) FROM {table} t
            JOIN ({MAP_SUBQUERY}) map ON t.{owner_col} = map.legacy_user_id
            """
        )
        metrics[f"legacy_fk_{table}"] = int(cur.fetchone()[0])

    cur.execute(
        f"""
        SELECT COUNT(*) FROM tbl_merchant_staff staff
        JOIN ({MAP_SUBQUERY}) map ON staff.merchant_id = map.legacy_user_id
        """
    )
    metrics["legacy_fk_tbl_merchant_staff"] = int(cur.fetchone()[0])

    cur.execute(
        f"""
        SELECT COUNT(*) FROM active_package ap
        JOIN ({MAP_SUBQUERY}) map ON ap.{active_pkg_col} = map.legacy_user_id
        """
    )
    metrics["legacy_fk_active_package"] = int(cur.fetchone()[0])

    return metrics


def merchant_snapshot(cur, email: str) -> dict | None:
    cur.execute(
        """
        SELECT m.id, u.id AS legacy_user_id
        FROM tbl_merchant m
        LEFT JOIN tbl_user u
            ON lower(trim(u.email)) = lower(trim(m.email)) AND u.deleted_at IS NULL
        WHERE lower(trim(m.email)) = lower(trim(%s)) AND m.deleted_at IS NULL
        """,
        (email,),
    )
    row = cur.fetchone()
    if not row:
        return None
    merchant_id, legacy_user_id = row
    snap: dict = {"email": email.strip().lower(), "merchant_id": merchant_id, "legacy_user_id": legacy_user_id}

    def count(table: str, preferred: str = "merchant_id", owner_id: int | None = None) -> int:
        if owner_id is None:
            return 0
        cur.execute("SELECT to_regclass(%s)", (f"public.{table}",))
        if not cur.fetchone()[0]:
            return 0
        column = merchant_owner_column(cur, table, preferred)
        if not column:
            return 0
        cur.execute(f"SELECT COUNT(*) FROM {table} WHERE {column} = %s", (owner_id,))
        return int(cur.fetchone()[0])

    snap["menus"] = count("tbl_menu", owner_id=merchant_id)
    snap["branches"] = count("tbl_branch", owner_id=merchant_id)
    snap["products"] = count("tbl_menu_products", owner_id=merchant_id)
    snap["categories"] = count("tbl_menu_category", owner_id=merchant_id)
    snap["smart_report_events"] = count("tbl_smart_report_events", owner_id=merchant_id)
    snap["batch_reports"] = count("tbl_batch_reports", owner_id=merchant_id)
    snap["accounting_entries"] = count("tbl_user_accounting_entry", owner_id=merchant_id)
    snap["staff"] = count("tbl_merchant_staff", preferred="merchant_id", owner_id=merchant_id)
    ap_col = merchant_owner_column(cur, "active_package") or "merchant_id"
    cur.execute(f"SELECT 1 FROM active_package WHERE {ap_col} = %s LIMIT 1", (merchant_id,))
    snap["active_package"] = cur.fetchone() is not None
    if legacy_user_id and legacy_user_id != merchant_id:
        snap["legacy_menus"] = count("tbl_menu", owner_id=legacy_user_id)
        snap["legacy_branches"] = count("tbl_branch", owner_id=legacy_user_id)
    return snap


def print_snapshots(label: str, snapshots: list[dict]) -> None:
    print(f"=== {label} merchant snapshots ===")
    for snap in snapshots:
        print(f"  {snap}")


def print_audit(label: str, metrics: dict, flyway: dict[str, bool], *, rename_applied: bool | None = None) -> None:
    print(f"=== {label} audit ===")
    for version, _, _ in CUTover_VERSIONS:
        print(f"  flyway V{version}: {'applied' if flyway[version] else 'MISSING'}")
    if rename_applied is not None:
        print(f"  flyway V{RENAME_VERSION[0]}: {'applied' if rename_applied else 'MISSING'}")
    for key, value in metrics.items():
        print(f"  {key}: {value}")


def cmd_audit(cfg: dict, label: str) -> int:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    flyway = {v: flyway_applied(cur, v) for v, _, _ in CUTover_VERSIONS}
    metrics = audit(cur)
    print_audit(label, metrics, flyway, rename_applied=flyway_applied(cur, RENAME_VERSION[0]))
    conn.close()
    blocking = (
        metrics["live_users_without_merchant"] > 0
        or metrics["phone_conflicts"] > 0
        or metrics["provider_subject_conflicts"] > 0
    )
    return 1 if blocking else 0


def cmd_migrate(cfg: dict, label: str, *, dry_run: bool) -> int:
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()
    flyway_before = {v: flyway_applied(cur, v) for v, _, _ in CUTover_VERSIONS}
    pending = [(v, d, s) for v, d, s in CUTover_VERSIONS if not flyway_before[v]]
    if not pending:
        print(f"[{label}] all cutover migrations already applied")
        conn.close()
        return 0
    print(f"[{label}] pending: {[v for v, _, _ in pending]}")
    if dry_run:
        conn.close()
        return 0
    try:
        for version, description, script_name in pending:
            path = MIGRATIONS / script_name
            sql = path.read_text(encoding="utf-8")
            print(f"[{label}] applying V{version} ...")
            cur.execute(sql)
            record_flyway(cur, version, description, script_name, path)
        conn.commit()
    except Exception as exc:
        conn.rollback()
        print(f"[{label}] migrate FAILED: {exc}", file=sys.stderr)
        conn.close()
        return 1
    metrics = audit(cur)
    flyway = {v: flyway_applied(cur, v) for v, _, _ in CUTover_VERSIONS}
    print_audit(
        f"{label} post-migrate",
        metrics,
        flyway,
        rename_applied=flyway_applied(cur, RENAME_VERSION[0]),
    )
    conn.close()
    return 0


def cmd_rename(cfg: dict, label: str, *, dry_run: bool) -> int:
    version, description, script_name = RENAME_VERSION
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()
    if flyway_applied(cur, version):
        print(f"[{label}] V{version} rename already applied")
        conn.close()
        return 0
    path = MIGRATIONS / script_name
    if dry_run:
        print(f"[{label}] would apply V{version} from {script_name}")
        conn.close()
        return 0
    try:
        sql = path.read_text(encoding="utf-8")
        print(f"[{label}] applying V{version} ...")
        cur.execute(sql)
        record_flyway(cur, version, description, script_name, path)
        conn.commit()
    except Exception as exc:
        conn.rollback()
        print(f"[{label}] rename FAILED: {exc}", file=sys.stderr)
        conn.close()
        return 1
    metrics = audit(cur)
    flyway = {v: flyway_applied(cur, v) for v, _, _ in CUTover_VERSIONS}
    print_audit(
        f"{label} post-rename",
        metrics,
        flyway,
        rename_applied=True,
    )
    conn.close()
    return 0


def cmd_snapshot(cfg: dict, label: str, emails: tuple[str, ...]) -> int:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    snaps = []
    for email in emails:
        snap = merchant_snapshot(cur, email)
        if snap:
            snaps.append(snap)
        else:
            print(f"[{label}] no merchant for {email}")
    print_snapshots(label, snaps)
    conn.close()
    return 0


def cmd_validate(cfg: dict, label: str) -> int:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    metrics = audit(cur)
    flyway = {v: flyway_applied(cur, v) for v, _, _ in CUTover_VERSIONS}
    rename_ok = flyway_applied(cur, RENAME_VERSION[0])
    print_audit(label, metrics, flyway, rename_applied=rename_ok)
    conn.close()
    ok = all(flyway[v] for v, _, _ in CUTover_VERSIONS)
    ok = ok and rename_ok
    ok = ok and metrics["live_users_without_merchant"] == 0
    ok = ok and metrics["phone_conflicts"] == 0
    ok = ok and metrics["provider_subject_conflicts"] == 0
    for key, value in metrics.items():
        if key.startswith("legacy_fk_") and value > 0:
            ok = False
    print(f"[{label}] validate: {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    env_kwargs = dict(choices=("prod", "stage", "both"), default="stage")
    sub = parser.add_subparsers(dest="command", required=True)
    audit_p = sub.add_parser("audit", help="Pre/post cutover metrics and flyway state")
    audit_p.add_argument("--env", **env_kwargs)
    migrate_p = sub.add_parser("migrate", help="Apply missing V156-V159 in order")
    migrate_p.add_argument("--env", **env_kwargs)
    migrate_p.add_argument("--dry-run", action="store_true")
    validate_p = sub.add_parser("validate", help="Fail if flyway or legacy FK checks fail")
    validate_p.add_argument("--env", **env_kwargs)
    rename_p = sub.add_parser("rename", help="Apply V160 column rename (merchant_id)")
    rename_p.add_argument("--env", **env_kwargs)
    rename_p.add_argument("--dry-run", action="store_true")
    snapshot_p = sub.add_parser("snapshot", help="Per-merchant row counts for smoke / before-after proof")
    snapshot_p.add_argument("--env", **env_kwargs)
    snapshot_p.add_argument(
        "--email",
        action="append",
        dest="emails",
        help="Merchant email (repeatable); defaults to built-in sample list",
    )
    args = parser.parse_args()

    targets: list[tuple[str, dict]] = []
    if args.env in ("prod", "both"):
        targets.append(("PROD", PROD))
    if args.env in ("stage", "both"):
        targets.append(("STAGE", STAGE))

    exit_code = 0
    for label, cfg in targets:
        if args.command == "audit":
            exit_code = max(exit_code, cmd_audit(cfg, label))
        elif args.command == "migrate":
            exit_code = max(exit_code, cmd_migrate(cfg, label, dry_run=args.dry_run))
        elif args.command == "rename":
            exit_code = max(exit_code, cmd_rename(cfg, label, dry_run=args.dry_run))
        elif args.command == "validate":
            exit_code = max(exit_code, cmd_validate(cfg, label))
        elif args.command == "snapshot":
            emails = tuple(args.emails) if args.emails else SNAPSHOT_SAMPLE_EMAILS
            exit_code = max(exit_code, cmd_snapshot(cfg, label, emails))
    raise SystemExit(exit_code)


if __name__ == "__main__":
    main()
