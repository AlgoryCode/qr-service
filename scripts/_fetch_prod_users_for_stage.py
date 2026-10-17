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
for uid in (25, 13):
    cur.execute(
        """
        SELECT id, email, first_name, last_name, provider, role, email_verified
        FROM tbl_user WHERE id=%s
        """,
        (uid,),
    )
    print(cur.fetchone())
cur.close()
conn.close()
