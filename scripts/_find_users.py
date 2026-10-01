import psycopg2

PROD = dict(
    host="185.184.210.52", port=5432,
    dbname="algoryqrdb", user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable", connect_timeout=10
)

conn = psycopg2.connect(**PROD)
conn.autocommit = False
cur = conn.cursor()

cur.execute("""
    SELECT id, email, first_name, last_name FROM tbl_user
    WHERE email ILIKE '%ayaroof%'
       OR email ILIKE '%ulash%' OR email ILIKE '%ulas%' OR email ILIKE '%bayram%'
       OR first_name ILIKE '%ayaroof%' OR first_name ILIKE '%ulas%'
       OR last_name ILIKE '%bayram%' OR last_name ILIKE '%ayaroof%'
    ORDER BY id
""")
users = cur.fetchall()
print("=== BULUNAN KULLANICILAR ===")
for r in users:
    print(r)

conn.close()
