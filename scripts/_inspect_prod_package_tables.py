import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
    connect_timeout=15,
)
conn = psycopg2.connect(**PROD)
cur = conn.cursor()
cur.execute(
    "SELECT column_name FROM information_schema.columns WHERE table_name='tbl_purchase' ORDER BY ordinal_position"
)
print("tbl_purchase:", [r[0] for r in cur.fetchall()])
cur.execute(
    "SELECT column_name FROM information_schema.columns WHERE table_name='tbl_user_entitlement' ORDER BY ordinal_position"
)
print("tbl_user_entitlement:", [r[0] for r in cur.fetchall()])
conn.close()
