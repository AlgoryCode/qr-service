import pathlib
import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)
PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

ROOT = pathlib.Path(__file__).resolve().parents[1]
V10 = ROOT / "src/main/resources/db/migration/V10__integration_approval_products.sql"
V11 = ROOT / "src/main/resources/db/migration/V11__ubereats_connections.sql"

MIGRATIONS = [
    ("10", "V10__integration_approval_products.sql", V10, "integration_jobs"),
    ("11", "V11__ubereats_connections.sql", V11, "ubereats_connections"),
]


def table_exists(cur, table: str) -> bool:
    cur.execute(
        """
        SELECT EXISTS (
          SELECT 1 FROM information_schema.tables
          WHERE table_schema = 'public' AND table_name = %s
        )
        """,
        (table,),
    )
    return bool(cur.fetchone()[0])


def flyway_version_exists(cur, version: str) -> bool:
    cur.execute(
        """
        SELECT EXISTS (
          SELECT 1 FROM information_schema.tables
          WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'
        )
        """
    )
    if not cur.fetchone()[0]:
        return False
    cur.execute(
        "SELECT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = %s)",
        (version,),
    )
    return bool(cur.fetchone()[0])


def next_flyway_installed_rank(cur) -> int:
    cur.execute("SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history")
    return int(cur.fetchone()[0])


def record_flyway(cur, version: str, description: str, script: str) -> None:
    if flyway_version_exists(cur, version):
        print(f"  flyway {version} already recorded")
        return
    rank = next_flyway_installed_rank(cur)
    cur.execute(
        """
        INSERT INTO flyway_schema_history (
          installed_rank, version, description, type, script, checksum,
          installed_by, installed_on, execution_time, success
        ) VALUES (
          %s, %s, %s, 'SQL', %s, NULL,
          current_user, NOW(), 0, TRUE
        )
        """,
        (rank, version, description, script),
    )
    print(f"  flyway {version} recorded as installed_rank={rank}")


def apply_db(name: str, cfg: dict) -> None:
    print(f"\n=== {name} ({cfg['dbname']}:{cfg['port']}) ===")
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()
    try:
        for version, script_name, path, probe_table in MIGRATIONS:
            exists = table_exists(cur, probe_table)
            print(f"  {probe_table} exists: {exists}")
            if not exists:
                print(f"  applying {script_name}...")
                cur.execute(path.read_text(encoding="utf-8"))
                if not table_exists(cur, probe_table):
                    raise RuntimeError(f"{probe_table} still missing after {script_name}")
                print(f"  {script_name} applied")
            else:
                print(f"  skipping SQL for {script_name}")
            description = script_name.split("__", 1)[1].removesuffix(".sql").replace("_", " ")
            record_flyway(cur, version, description, script_name)

        print("  verify:")
        for _, _, _, probe_table in MIGRATIONS:
            print(f"    {probe_table}: {table_exists(cur, probe_table)}")
        cur.execute(
            """
            SELECT version, script, success
            FROM flyway_schema_history
            WHERE version IN ('10', '11')
            ORDER BY installed_rank
            """
        )
        rows = cur.fetchall()
        print("  flyway rows:", rows)
        conn.commit()
        print(f"  {name} committed")
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()
        conn.close()


def main() -> None:
    apply_db("STAGE", STAGE)
    apply_db("PROD", PROD)
    print("\nDone.")


if __name__ == "__main__":
    main()
