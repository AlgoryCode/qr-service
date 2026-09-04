import psycopg2

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)

conn = psycopg2.connect(**STAGE)
cur = conn.cursor()

print("=== ubereats_connections ===")
cur.execute(
    """
    SELECT id, user_id, menu_id, store_id, status, last_error, created_at, updated_at
    FROM ubereats_connections
    ORDER BY id
    """
)
rows = cur.fetchall()
print("count:", len(rows))
for r in rows:
    print(r)

print("\n=== tbl_trendyol_go_connection (user 22) ===")
cur.execute(
    """
    SELECT id, user_id, branch_id, seller_id, restaurant_id, restaurant_name, status, last_error
    FROM tbl_trendyol_go_connection
    WHERE user_id = 22
    """
)
for r in cur.fetchall():
    print(r)

print("\n=== menus user 22 ===")
cur.execute(
    """
    SELECT menu_id, business_name, branch_id, active, is_deleted
    FROM tbl_menu
    WHERE user_id = 22
    ORDER BY menu_id
    """
)
for r in cur.fetchall():
    print(r)

cur.close()
conn.close()
