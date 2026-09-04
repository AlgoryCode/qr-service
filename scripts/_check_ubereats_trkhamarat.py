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


def run(name, cfg):
    print(f"\n=== {name} ===")
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()

    cur.execute("SELECT COUNT(*) FROM ubereats_connections")
    print("ubereats_connections:", cur.fetchone()[0])
    cur.execute(
        "SELECT id, user_id, menu_id, store_id, status, created_at FROM ubereats_connections"
    )
    for r in cur.fetchall():
        print(" ", r)

    cur.execute(
        """
        SELECT id, email, first_name, last_name
        FROM tbl_user
        WHERE email ILIKE %s
        """,
        ("%trkhamarat%",),
    )
    users = cur.fetchall()
    print("users:", users)
    user_ids = [u[0] for u in users]

    if user_ids:
        cur.execute(
            """
            SELECT menu_id, user_id, business_name, active, is_deleted
            FROM tbl_menu
            WHERE user_id = ANY(%s)
            ORDER BY menu_id
            """,
            (user_ids,),
        )
        menus = cur.fetchall()
        print("menus for user:")
        for m in menus:
            print(" ", m)

        cur.execute(
            """
            SELECT menu_id, user_id, business_name
            FROM tbl_menu
            WHERE user_id = ANY(%s)
              AND (
                business_name ILIKE %s
                OR business_name ILIKE %s
                OR business_name ILIKE %s
              )
            """,
            (user_ids, "%mexican%", "%doner%", "%döner%"),
        )
        print("mexican/doner menus:", cur.fetchall())

    cur.execute(
        """
        SELECT menu_id, user_id, business_name
        FROM tbl_menu
        WHERE business_name ILIKE %s
           OR business_name ILIKE %s
           OR business_name ILIKE %s
        LIMIT 20
        """,
        ("%mexican%", "%doner%", "%döner%"),
    )
    print("all mexican/doner menus:", cur.fetchall())

    cur.execute("SELECT COUNT(*) FROM integration_jobs")
    print("integration_jobs:", cur.fetchone()[0])
    cur.execute("SELECT COUNT(*) FROM integration_pending_products")
    print("pending_products:", cur.fetchone()[0])

    cur.close()
    conn.close()


if __name__ == "__main__":
    run("STAGE", STAGE)
    run("PROD", PROD)
