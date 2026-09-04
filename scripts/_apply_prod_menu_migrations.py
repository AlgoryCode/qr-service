import pathlib
import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

ROOT = pathlib.Path(__file__).resolve().parents[1]
MIGRATIONS = {
    "V7": ROOT / "src/main/resources/db/migration/V7__menu_scoped_categories.sql",
    "V8": ROOT / "src/main/resources/db/migration/V8__menu_category_user_id.sql",
    "V9": ROOT / "src/main/resources/db/migration/V9__menu_category_cover_image.sql",
}


def table_exists(cur, table):
    cur.execute(
        """
        SELECT EXISTS (
          SELECT 1 FROM information_schema.tables
          WHERE table_schema='public' AND table_name=%s
        )
        """,
        (table,),
    )
    return cur.fetchone()[0]


def column_exists(cur, table, column):
    cur.execute(
        """
        SELECT EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema='public' AND table_name=%s AND column_name=%s
        )
        """,
        (table, column),
    )
    return cur.fetchone()[0]


def run_sql_file(cur, path):
    sql = path.read_text(encoding="utf-8")
    cur.execute(sql)


def main():
    conn = psycopg2.connect(**PROD)
    conn.autocommit = False
    cur = conn.cursor()

    try:
        print("Before:")
        print("  tbl_menu_category exists:", table_exists(cur, "tbl_menu_category"))
        print("  image_url exists:", column_exists(cur, "tbl_menu_category", "image_url"))

        if not table_exists(cur, "tbl_menu_category"):
            print("\nApplying V7 (menu-scoped categories)...")
            run_sql_file(cur, MIGRATIONS["V7"])
            print("V7 applied.")
        else:
            print("\nSkipping V7 (tbl_menu_category already exists).")

        if not column_exists(cur, "tbl_menu_category", "user_id"):
            print("Applying V8 (user_id)...")
            run_sql_file(cur, MIGRATIONS["V8"])
            print("V8 applied.")
        else:
            print("Skipping V8 (user_id already exists).")

        if not column_exists(cur, "tbl_menu_category", "image_url"):
            print("Applying V9 (image_url/image_key)...")
            run_sql_file(cur, MIGRATIONS["V9"])
            print("V9 applied.")
        else:
            print("Skipping V9 (image_url already exists).")

        cur.execute("SELECT COUNT(*) FROM tbl_menu_category WHERE is_deleted = false")
        print("menu categories:", cur.fetchone()[0])
        cur.execute("SELECT COUNT(*) FROM tbl_menu_sub_category WHERE is_deleted = false")
        print("menu sub categories:", cur.fetchone()[0])
        cur.execute(
            """
            SELECT COUNT(*) FROM tbl_menu_products p
            WHERE p.is_deleted = false
              AND NOT EXISTS (
                SELECT 1 FROM tbl_menu_sub_category sc WHERE sc.id = p.sub_category_id
              )
            """
        )
        orphans = cur.fetchone()[0]
        print("orphan products:", orphans)
        if orphans:
            raise RuntimeError(f"{orphans} products without menu-scoped subcategory")

        conn.commit()
        print("\nProd schema sync committed successfully.")
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    main()
