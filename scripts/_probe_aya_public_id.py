import json
import pathlib
import urllib.request

import psycopg2

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

ROOT = pathlib.Path(__file__).resolve().parents[1]
V88 = ROOT / "src/main/resources/db/migration/V88__menu_public_id.sql"


def column_exists(cur, table: str, column: str) -> bool:
    cur.execute(
        """
        SELECT EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema='public' AND table_name=%s AND column_name=%s
        )
        """,
        (table, column),
    )
    return bool(cur.fetchone()[0])


def main() -> None:
    conn = psycopg2.connect(**PROD)
    conn.autocommit = False
    cur = conn.cursor()

    has_col = column_exists(cur, "tbl_menu", "public_id")
    print("public_id column:", has_col)
    if not has_col:
        print("Applying V88...")
        cur.execute(V88.read_text(encoding="utf-8"))
        conn.commit()
        print("V88 applied")
    else:
        nulls = 0
        cur.execute("SELECT COUNT(*) FROM tbl_menu WHERE public_id IS NULL OR btrim(public_id) = ''")
        nulls = cur.fetchone()[0]
        print("empty public_id rows:", nulls)
        if nulls:
            print("Backfilling empty public_id...")
            cur.execute(V88.read_text(encoding="utf-8"))
            conn.commit()
            print("backfill done")

    cur.execute(
        """
        SELECT menu_id, qr_id, public_id, business_name, active, is_deleted
        FROM tbl_menu
        WHERE qr_id = 35
           OR business_name ILIKE '%aya%roof%'
           OR email ILIKE '%ayaroof%'
        ORDER BY menu_id
        """
    )
    rows = cur.fetchall()
    print("aya menus:", rows)

    public_id = None
    for menu_id, qr_id, pid, business_name, active, is_deleted in rows:
        if qr_id == 35:
            public_id = pid
            print("picked", (menu_id, qr_id, pid, business_name, active, is_deleted))
            break
    if not public_id and rows:
        public_id = rows[0][2]
        print("fallback", rows[0])

    cur.close()
    conn.close()

    if not public_id:
        raise SystemExit("no public id")

    print("PUBLIC_ID=", public_id)
    base = "https://prod.qrapi.algorycode.com"
    paths = [
        f"/menu/public/{public_id}",
        f"/menu/public/{public_id}/products?page=0&size=5",
        f"/menu/public/{public_id}/categories?page=0&size=20",
        "/menu/public/legacy-qr/35/public-id",
    ]
    for path in paths:
        url = base + path
        try:
            with urllib.request.urlopen(url, timeout=30) as resp:
                body = resp.read().decode("utf-8", errors="replace")
                data = json.loads(body)
                print(f"\n=== {resp.status} {path}")
                if path.endswith("/public-id"):
                    print(data)
                elif "products" in data:
                    menu = data.get("menu") or {}
                    print(
                        "menu",
                        {k: menu.get(k) for k in ("publicId", "businessName")},
                        "bootstrap_products",
                        len(data.get("products") or []),
                    )
                else:
                    content = data.get("content") or []
                    print("content_len", len(content), "hasNext", data.get("hasNext"))
                    if content and "productId" in content[0]:
                        print(
                            "first_products",
                            [(c.get("productId"), c.get("name"), c.get("price")) for c in content[:5]],
                        )
                    elif content and "name" in content[0]:
                        print("first_names", [c.get("name") for c in content[:8]])
        except Exception as exc:
            print(f"\n=== FAIL {path}: {exc}")


def debug_schema_and_errors() -> None:
    import urllib.error

    conn = psycopg2.connect(**PROD)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT column_name, data_type, is_nullable
        FROM information_schema.columns
        WHERE table_schema='public' AND table_name='tbl_menu'
          AND column_name IN ('public_id','public_access_enabled','active','is_deleted','qr_id')
        ORDER BY 1
        """
    )
    print("cols", cur.fetchall())
    cur.execute(
        """
        SELECT menu_id, qr_id, public_id, active, is_deleted, public_access_enabled
        FROM tbl_menu WHERE qr_id=35
        """
    )
    print("row", cur.fetchone())
    cur.close()
    conn.close()

    pid = "scxtG9KR5FrK0RlLjcYPUw"
    for url in [
        f"https://prod.qrapi.algorycode.com/menu/public/legacy-qr/35/public-id",
        f"https://prod.qrapi.algorycode.com/menu/public/{pid}",
    ]:
        try:
            with urllib.request.urlopen(url, timeout=20) as resp:
                print(resp.status, url, resp.read()[:300])
        except urllib.error.HTTPError as exc:
            print(exc.code, url, exc.read().decode("utf-8", "replace"))


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == "debug":
        debug_schema_and_errors()
    else:
        main()
