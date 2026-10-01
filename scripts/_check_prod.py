import psycopg2

for pw in ['algorcode_stage', 'postgres_stage', 'algorcode', 'postgres', 'AlgorCode']:
    try:
        conn = psycopg2.connect(
            host="185.184.210.52", port=5432,
            dbname="algoryqrdb", user="postgres",
            password=pw, sslmode="disable", connect_timeout=5
        )
        print("BASARILI:", pw)
        cur = conn.cursor()
        cur.execute("SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5")
        for r in cur.fetchall():
            print(" ", r)
        conn.close()
        break
    except psycopg2.OperationalError as e:
        if "password authentication" in str(e):
            print("pw yanlis:", pw)
        else:
            print("hata:", pw, str(e)[:60])
