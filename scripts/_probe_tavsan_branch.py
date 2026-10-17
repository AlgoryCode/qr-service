import psycopg2

conn = psycopg2.connect(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)
cur = conn.cursor()
cur.execute(
    "SELECT id, user_id, name, is_deleted FROM tbl_branch WHERE id = 1 OR user_id IN (13, 29) ORDER BY id"
)
print("branches", cur.fetchall())
cur.execute(
    "SELECT menu_id, user_id, branch_id, business_name, is_deleted FROM tbl_menu WHERE menu_id = 13 OR user_id = 13"
)
print("menus", cur.fetchall())
cur.execute("SELECT COUNT(*) FROM tbl_restaurant_table WHERE branch_id = 1")
print("restaurant_table count branch 1", cur.fetchone())
cur.execute(
    "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE '%table%' AND table_name LIKE '%restaurant%'"
)
print("table names", cur.fetchall())
for t in ("tbl_restaurant_table", "tbl_menu_table", "tbl_table"):
    cur.execute("SELECT to_regclass(%s)", (f"public.{t}",))
    if cur.fetchone()[0]:
        cur.execute(f"SELECT COUNT(*) FROM {t} WHERE branch_id = 1")
        print(t, cur.fetchone()[0])
cur.execute(
    """
    SELECT column_name FROM information_schema.columns
    WHERE table_name = 'tbl_branch' ORDER BY ordinal_position
    """
)
print("branch cols", [r[0] for r in cur.fetchall()])
conn.close()
