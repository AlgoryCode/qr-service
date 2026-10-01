import json
import urllib.error
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

PUBLIC_ID = "scxtG9KR5FrK0RlLjcYPUw"


def enable_public_access() -> None:
    conn = psycopg2.connect(**PROD)
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute(
        """
        UPDATE tbl_menu
        SET public_access_enabled = TRUE,
            public_access_disabled_reason = NULL
        WHERE qr_id = 35
        RETURNING menu_id, qr_id, public_id, public_access_enabled, public_access_disabled_reason
        """
    )
    print("updated", cur.fetchone())
    cur.close()
    conn.close()


def hit(path: str) -> None:
    url = "https://prod.qrapi.algorycode.com" + path
    try:
        with urllib.request.urlopen(url, timeout=30) as resp:
            body = resp.read().decode("utf-8", "replace")
            data = json.loads(body)
            print(f"OK {resp.status} {path}")
            if "content" in data:
                content = data["content"] or []
                print("  count", len(content), "hasNext", data.get("hasNext"))
                if content and "name" in content[0] and "productId" in content[0]:
                    print("  sample", [(c.get("productId"), c.get("name"), c.get("price")) for c in content[:5]])
                elif content:
                    print("  sample", [c.get("name") for c in content[:8]])
            elif "menu" in data:
                menu = data.get("menu") or {}
                print("  menu", {k: menu.get(k) for k in ("publicId", "businessName", "slogan")})
                print("  products", len(data.get("products") or []))
            else:
                print(" ", data)
    except urllib.error.HTTPError as exc:
        print(f"FAIL {exc.code} {path} {exc.read().decode('utf-8', 'replace')}")


if __name__ == "__main__":
    enable_public_access()
    hit(f"/menu/public/legacy-qr/35/public-id")
    hit(f"/menu/public/{PUBLIC_ID}")
    hit(f"/menu/public/{PUBLIC_ID}/products?page=0&size=5")
    hit(f"/menu/public/{PUBLIC_ID}/categories?page=0&size=20")
