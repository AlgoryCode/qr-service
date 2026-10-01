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
    UPDATE tbl_purchase
    SET purchase_type = 'SYSTEM_GRANT',
        system_managed = false,
        starts_at = NOW(),
        expires_at = NOW() + INTERVAL '15 days'
    WHERE id = 141
""")
print("Purchase #141: SYSTEM_GRANT, system_managed=false, 15 gun")

cur.execute("SELECT id, status, purchase_type, system_managed, starts_at::date, expires_at::date FROM tbl_purchase WHERE id=141")
print("Guncel:", cur.fetchone())

conn.commit()
conn.close()
print("TAMAM")
