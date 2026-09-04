import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)
STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
)

for name, cfg in [("STAGE", STAGE), ("PROD", PROD)]:
    c = psycopg2.connect(**cfg)
    cur = c.cursor()
    cur.execute(
        "SELECT id, user_id, branch_id, seller_id, restaurant_id, restaurant_name, status FROM ubereats_connections"
    )
    print(name, "connections", cur.fetchall())
    cur.execute("SELECT version, script FROM flyway_schema_history WHERE version='12'")
    print(name, "flyway12", cur.fetchall())
    cur.close()
    c.close()
