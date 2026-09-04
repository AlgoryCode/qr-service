import psycopg2

STAGE = dict(
    host="185.184.210.52", port=5433, dbname="algoryqrdb-stage",
    user="postgres", password="postgres_stage", sslmode="disable",
)
PROD = dict(
    host="185.184.210.52", port=5432, dbname="algoryqrdb",
    user="postgres", password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

TABLES = [
    "tbl_main_category",
    "tbl_sub_category",
    "tbl_menu_category",
    "tbl_menu_sub_category",
    "tbl_menu_products",
    "tbl_descriptor_category",
]


def dump_table(cfg, table):
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT EXISTS (
          SELECT 1 FROM information_schema.tables
          WHERE table_schema='public' AND table_name=%s
        )
        """,
        (table,),
    )
    exists = cur.fetchone()[0]
    if not exists:
        cur.close()
        conn.close()
        return False, []
    cur.execute(
        """
        SELECT column_name, data_type, character_maximum_length, is_nullable
        FROM information_schema.columns
        WHERE table_schema='public' AND table_name=%s
        ORDER BY ordinal_position
        """,
        (table,),
    )
    cols = cur.fetchall()
    cur.close()
    conn.close()
    return True, cols


def main():
    for table in TABLES:
        print(f"\n===== {table} =====")
        for env, cfg in [("STAGE", STAGE), ("PROD", PROD)]:
            exists, cols = dump_table(cfg, table)
            print(f"{env}: {'EXISTS' if exists else 'MISSING'}")
            for col in cols:
                print(f"  {col[0]} {col[1]}" + (f"({col[2]})" if col[2] else "") + f" null={col[3]}")
        stage_exists, stage_cols = dump_table(STAGE, table)
        prod_exists, prod_cols = dump_table(PROD, table)
        if stage_exists and prod_exists:
            stage_names = {c[0] for c in stage_cols}
            prod_names = {c[0] for c in prod_cols}
            only_stage = sorted(stage_names - prod_names)
            only_prod = sorted(prod_names - stage_names)
            if only_stage:
                print("ONLY STAGE:", only_stage)
            if only_prod:
                print("ONLY PROD:", only_prod)


if __name__ == "__main__":
    main()
