from __future__ import annotations

import json
import mimetypes
import ssl
import urllib.request
import uuid

import psycopg2

API = "https://prod.qrapi.algorycode.com"
MENU_ID = 16
USER_ID = 20
PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)
SSL_CTX = ssl.create_default_context()


def api(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as resp:
        return json.loads(resp.read())


def main() -> None:
    conn = psycopg2.connect(**PROD)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT p.product_id, p.name, p.image_url, mc.id
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s
          AND p.is_deleted = false
          AND mc.slug = 'etler'
        ORDER BY p.sort_order, p.product_id
        """,
        (MENU_ID,),
    )
    rows = cur.fetchall()
    print("ETLER PRODUCTS:")
    for row in rows:
        print(row[0], row[1], "img=" + ("yes" if row[2] else "no"), (row[2] or "")[:70])

    cur.execute(
        """
        SELECT p.name, p.image_url, mc.id
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s
          AND p.is_deleted = false
          AND mc.slug = 'etler'
          AND p.image_url IS NOT NULL
          AND p.image_url <> ''
          AND (
            p.name ILIKE '%%chop%%'
            OR p.name ILIKE '%%pirzola%%'
            OR lower(p.name) LIKE '%%chop%%'
          )
        ORDER BY
          CASE
            WHEN p.name ILIKE '%%chop%%' THEN 0
            WHEN p.name ILIKE '%%pirzola%%' THEN 1
            ELSE 2
          END,
          p.sort_order,
          p.product_id
        LIMIT 1
        """,
        (MENU_ID,),
    )
    picked = cur.fetchone()
    if not picked:
        raise SystemExit("Chop/Pirzola product with image not found in Etler")
    product_name, image_url, cat_id = picked
    print("PICKED", product_name, image_url, "cat", cat_id)

    admin = api("POST", "/admin/auth/sessions", body={"email": "admin@example.com", "password": "Admin123!"})
    token = api("POST", f"/admin/users/{USER_ID}/impersonation-sessions", token=admin["accessToken"], body={})["accessToken"]

    req = urllib.request.Request(image_url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as resp:
        data = resp.read()
        content_type = (resp.headers.get("Content-Type") or "image/jpeg").split(";")[0].strip()
    ext = mimetypes.guess_extension(content_type) or ".jpg"
    if "png" in content_type:
        ext = ".png"
    elif "jpeg" in content_type or "jpg" in content_type:
        ext = ".jpg"

    filename = f"etler-chop-cover{ext}"
    boundary = f"----AlgoryBoundary{uuid.uuid4().hex}"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
    upload_req = urllib.request.Request(
        f"{API}/menu/{MENU_ID}/categories/{cat_id}/cover",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(upload_req, timeout=90, context=SSL_CTX) as resp:
        payload = json.loads(resp.read())
    cover_url = payload.get("imageUrl") or payload.get("image_url")
    print("COVER", cover_url)
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
