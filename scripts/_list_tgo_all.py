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

for name, cfg in [("STAGE", STAGE), ("PROD", PROD)]:
    c = psycopg2.connect(**cfg)
    cur = c.cursor()
    cur.execute("SELECT id, user_id, branch_id, seller_id, restaurant_id, restaurant_name, status FROM tbl_trendyol_go_connection")
    print(name, cur.fetchall())
    cur.close()
    c.close()
