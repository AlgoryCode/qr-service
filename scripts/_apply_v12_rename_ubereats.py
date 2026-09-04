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

SQL = pathlib.Path(
    r"c:\Users\Tarik\Desktop\Services\qr-service\src\main\resources\db\migration\V12__rename_trendyol_go_to_ubereats.sql"
).read_text(encoding="utf-8")


def table_exists(cur, name: str) -> bool:
    cur.execute(
        """
        SELECT EXISTS (
          SELECT 1 FROM information_schema.tables
          WHERE table_schema='public' AND table_name=%s
        )
        """,
        (name,),
    )
    return bool(cur.fetchone()[0])


def flyway_has(cur, version: str) -> bool:
    cur.execute(
        """
        SELECT EXISTS (
          SELECT 1 FROM information_schema.tables
          WHERE table_schema='public' AND table_name='flyway_schema_history'
        )
        """
    )
    if not cur.fetchone()[0]:
        return False
    cur.execute("SELECT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version=%s)", (version,))
    return bool(cur.fetchone()[0])


def record_flyway(cur, version: str, script: str, description: str) -> None:
    if flyway_has(cur, version):
        print("  flyway already has", version)
        return
    cur.execute("SELECT COALESCE(MAX(installed_rank),0)+1 FROM flyway_schema_history")
    rank = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO flyway_schema_history (
          installed_rank, version, description, type, script, checksum,
          installed_by, installed_on, execution_time, success
        ) VALUES (%s,%s,%s,'SQL',%s,NULL,current_user,NOW(),0,TRUE)
        """,
        (rank, version, description, script),
    )
    print("  flyway recorded", version, "rank", rank)


def apply(name: str, cfg: dict) -> None:
    print(f"\n=== {name} ===")
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()
    try:
        print(" before:")
        for t in [
            "tbl_trendyol_go_connection",
            "tbl_trendyol_go_order",
            "ubereats_connections",
            "ubereats_menu_connections",
            "ubereats_orders",
        ]:
            print(f"  {t}: {table_exists(cur, t)}")

        if table_exists(cur, "ubereats_orders") and table_exists(cur, "ubereats_connections") and not table_exists(
            cur, "tbl_trendyol_go_connection"
        ):
            print("  already renamed; skip SQL")
        else:
            cur.execute(SQL)
            print("  V12 SQL applied")

        record_flyway(
            cur,
            "12",
            "V12__rename_trendyol_go_to_ubereats.sql",
            "rename trendyol go to ubereats",
        )

        print(" after:")
        for t in [
            "tbl_trendyol_go_connection",
            "tbl_trendyol_go_order",
            "ubereats_connections",
            "ubereats_menu_connections",
            "ubereats_orders",
        ]:
            print(f"  {t}: {table_exists(cur, t)}")

        conn.commit()
        print("  committed")
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    apply("STAGE", STAGE)
    apply("PROD", PROD)
