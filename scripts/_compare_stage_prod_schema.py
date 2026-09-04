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


def get_schema(cfg):
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT table_name, column_name, data_type, character_maximum_length, is_nullable, column_default
        FROM information_schema.columns
        WHERE table_schema = 'public'
        ORDER BY table_name, ordinal_position
        """
    )
    cols = cur.fetchall()
    cur.execute(
        """
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
        ORDER BY table_name
        """
    )
    tables = {r[0] for r in cur.fetchall()}
    cur.close()
    conn.close()
    schema = {}
    for table, column, data_type, max_len, nullable, default in cols:
        schema.setdefault(table, {})[column] = {
            "type": data_type,
            "max_len": max_len,
            "nullable": nullable,
            "default": default,
        }
    return tables, schema


def get_flyway(cfg):
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    try:
        cur.execute(
            "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
        )
        rows = cur.fetchall()
    except Exception as exc:
        rows = [("ERR", str(exc), False)]
    cur.close()
    conn.close()
    return rows


def main():
    stage_tables, stage_schema = get_schema(STAGE)
    prod_tables, prod_schema = get_schema(PROD)
    print(f"stage tables: {len(stage_tables)} | prod tables: {len(prod_tables)}")

    missing_tables = sorted(stage_tables - prod_tables)
    extra_prod_tables = sorted(prod_tables - stage_tables)

    print("\n=== TABLES only on STAGE ===")
    for table in missing_tables:
        print(f"  {table}")

    print("\n=== TABLES only on PROD ===")
    for table in extra_prod_tables:
        print(f"  {table}")

    missing_cols = []
    type_diffs = []
    for table in sorted(stage_tables & prod_tables):
        for column, meta in stage_schema[table].items():
            if column not in prod_schema[table]:
                missing_cols.append((table, column, meta))
            else:
                prod_meta = prod_schema[table][column]
                if prod_meta["type"] != meta["type"] or prod_meta["max_len"] != meta["max_len"]:
                    type_diffs.append((table, column, meta, prod_meta))

    print("\n=== COLUMNS on STAGE but missing on PROD ===")
    if not missing_cols:
        print("  (none)")
    for table, column, meta in missing_cols:
        max_len = f"({meta['max_len']})" if meta["max_len"] else ""
        print(
            f"  {table}.{column} {meta['type']}{max_len} "
            f"nullable={meta['nullable']} default={meta['default']}"
        )

    print("\n=== TYPE DIFFS ===")
    if not type_diffs:
        print("  (none)")
    for table, column, stage_meta, prod_meta in type_diffs:
        print(f"  {table}.{column}: stage={stage_meta} prod={prod_meta}")

    print("\n=== FLYWAY STAGE ===")
    for row in get_flyway(STAGE):
        print(f"  {row}")

    print("\n=== FLYWAY PROD ===")
    for row in get_flyway(PROD):
        print(f"  {row}")

    return missing_tables, missing_cols


if __name__ == "__main__":
    main()
