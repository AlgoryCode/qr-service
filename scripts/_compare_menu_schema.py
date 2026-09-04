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

MENU_TABLE_PATTERNS = (
    "tbl_menu_category",
    "tbl_menu_sub_category",
    "tbl_menu_products",
    "tbl_menu",
    "tbl_descriptor_category",
    "tbl_menu_tag",
    "tbl_menu_allergen",
)


def list_menu_tables(cfg):
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
          AND (
            table_name LIKE 'tbl_menu%%'
            OR table_name LIKE '%%category%%'
            OR table_name LIKE '%%taxonomy%%'
          )
        ORDER BY table_name
        """
    )
    rows = [r[0] for r in cur.fetchall()]
    cur.close()
    conn.close()
    return rows


def table_columns(cfg, table):
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT column_name, data_type, character_maximum_length, is_nullable
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = %s
        ORDER BY ordinal_position
        """,
        (table,),
    )
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return {r[0]: {"type": r[1], "max_len": r[2], "nullable": r[3]} for r in rows}


def main():
    for env, cfg in [("STAGE", STAGE), ("PROD", PROD)]:
        print(f"\n===== {env} menu-related tables =====")
        for table in list_menu_tables(cfg):
            print(f"  {table}")

    focus = ["tbl_menu_category", "tbl_menu_sub_category"]
    for table in focus:
        print(f"\n===== {table} columns =====")
        stage_cols = table_columns(STAGE, table) if table in list_menu_tables(STAGE) else None
        prod_cols = table_columns(PROD, table) if table in list_menu_tables(PROD) else None
        print("STAGE exists:", stage_cols is not None)
        if stage_cols:
            for c, m in stage_cols.items():
                print(f"  {c}: {m}")
        print("PROD exists:", prod_cols is not None)
        if prod_cols:
            for c, m in prod_cols.items():
                print(f"  {c}: {m}")
        if stage_cols and prod_cols:
            missing_on_prod = set(stage_cols) - set(prod_cols)
            missing_on_stage = set(prod_cols) - set(stage_cols)
            if missing_on_prod:
                print("MISSING ON PROD:", sorted(missing_on_prod))
            if missing_on_stage:
                print("MISSING ON STAGE:", sorted(missing_on_stage))


if __name__ == "__main__":
    main()
