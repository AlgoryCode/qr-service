import psycopg2

conn = psycopg2.connect(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)
cur = conn.cursor()
cur.execute("SELECT COUNT(*) FROM tbl_menu_product WHERE menu_id=8 AND is_deleted=false")
print("products", cur.fetchone()[0])
cur.execute(
    """
    SELECT email FROM tbl_user
    WHERE email ILIKE '%ayaroof%' OR email ILIKE '%rooflounge%' OR email ILIKE '%aya.roof%'
    """
)
print("aya users", cur.fetchall())
cur.execute(
    """
    SELECT menu_id, business_name, email, user_id
    FROM tbl_menu
    WHERE email ILIKE '%ayaroof%' OR email ILIKE '%rooflounge%' OR business_name ILIKE '%aya%roof%'
    """
)
print("aya menus", cur.fetchall())
cur.close()
conn.close()
