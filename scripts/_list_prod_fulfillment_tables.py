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
    """
    SELECT table_name FROM information_schema.tables
    WHERE table_schema = 'public'
      AND (
        table_name LIKE '%fulfill%'
        OR table_name LIKE '%active%'
        OR table_name LIKE '%entitle%'
        OR table_name LIKE '%purchase%'
        OR table_name LIKE '%demo%'
      )
    ORDER BY 1
    """
)
print("\n".join(r[0] for r in cur.fetchall()))
conn.close()
