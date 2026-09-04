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

SCRIPT = "V75__rename_trendyol_go_to_ubereats.sql"
DESC = "rename trendyol go to ubereats"


def fix(name, cfg):
    print("===", name, "===")
    conn = psycopg2.connect(**cfg)
    conn.autocommit = False
    cur = conn.cursor()
    try:
        cur.execute(
            "DELETE FROM flyway_schema_history WHERE script = %s OR (version = %s AND script LIKE %s)",
            ("V12__rename_trendyol_go_to_ubereats.sql", "12", "%rename_trendyol_go%"),
        )
        print(" deleted stale rows", cur.rowcount)
        cur.execute("SELECT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version=%s)", ("75",))
        if cur.fetchone()[0]:
            print(" V75 already present")
        else:
            cur.execute("SELECT COALESCE(MAX(installed_rank),0)+1 FROM flyway_schema_history")
            rank = cur.fetchone()[0]
            cur.execute(
                """
                INSERT INTO flyway_schema_history (
                  installed_rank, version, description, type, script, checksum,
                  installed_by, installed_on, execution_time, success
                ) VALUES (%s,'75',%s,'SQL',%s,NULL,current_user,NOW(),0,TRUE)
                """,
                (rank, DESC, SCRIPT),
            )
            print(" inserted V75 rank", rank)
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()
        conn.close()


fix("STAGE", STAGE)
fix("PROD", PROD)
