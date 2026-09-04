import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

conn = psycopg2.connect(**PROD)
cur = conn.cursor()

cur.execute(
    """
    SELECT column_name FROM information_schema.columns
    WHERE table_schema='public' AND table_name='tbl_trendyol_go_connection'
    ORDER BY ordinal_position
    """
)
print("tgo cols:", [r[0] for r in cur.fetchall()])

cur.execute("SELECT * FROM tbl_trendyol_go_connection ORDER BY id")
rows = cur.fetchall()
print("connections count:", len(rows))
for r in rows:
    print(r)

cur.execute(
    """
    SELECT column_name FROM information_schema.columns
    WHERE table_schema='public' AND table_name='tbl_branch'
    ORDER BY ordinal_position
    """
)
print("branch cols:", [r[0] for r in cur.fetchall()])

cur.execute(
    """
    SELECT id, user_id, name, is_deleted, created_at
    FROM tbl_branch
    WHERE user_id = 1
    ORDER BY id
    """
)
print("branches user 1:")
for r in cur.fetchall():
    print(" ", r)

cur.execute(
    """
    SELECT menu_id, user_id, business_name, branch_id, active, is_deleted
    FROM tbl_menu
    WHERE user_id = 1
    ORDER BY menu_id
    """
)
print("menus user 1:")
for r in cur.fetchall():
    print(" ", r)

cur.close()
conn.close()
