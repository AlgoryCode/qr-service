from __future__ import annotations

import json
import mimetypes
import ssl
import urllib.request
import uuid
from datetime import datetime, timezone

import psycopg2

API = "https://prod.qrapi.algorycode.com"
SSL_CTX = ssl.create_default_context()
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


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def api(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as resp:
        return json.loads(resp.read())


def main() -> None:
    admin = api(
        "POST",
        "/dashboard/auth/login",
        body={"email": "admin@example.com", "password": "Admin123!"},
    )
    imp = api(
        "POST",
        "/admin/users/21/impersonate",
        token=admin["accessToken"],
        body={},
    )
    token = imp["accessToken"]

    conn = psycopg2.connect(**PROD)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT p.image_url, mc.id
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = 17
          AND p.is_deleted = false
          AND mc.slug = %s
          AND p.name ILIKE %s
        LIMIT 1
        """,
        ("kadeh_icecekler", "%Prosecco Sparkling Wine Glass%"),
    )
    row = cur.fetchone()
    if not row or not row[0]:
        raise SystemExit("Prosecco Sparkling Wine Glass image not found")
    image_url, cat_id = row
    print("source", image_url)
    print("category", cat_id)

    req = urllib.request.Request(image_url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as resp:
        data = resp.read()
        content_type = resp.headers.get("Content-Type", "image/jpeg").split(";")[0].strip()

    ext = mimetypes.guess_extension(content_type) or ".jpg"
    if "png" in content_type:
        ext = ".png"
    filename = f"kadeh-cover{ext}"
    boundary = f"----AlgoryBoundary{uuid.uuid4().hex}"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
    upload_req = urllib.request.Request(
        f"{API}/menu/17/categories/{cat_id}/cover",
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
    cover_url = payload.get("imageUrl") or payload.get("image_url") or image_url
    print("PROD cover", cover_url)
    cur.close()
    conn.close()

    conn = psycopg2.connect(**STAGE)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT mc.id
        FROM tbl_menu_category mc
        JOIN tbl_menu m ON m.menu_id = mc.menu_id
        JOIN tbl_user u ON u.id = m.user_id
        WHERE u.email = %s AND mc.slug = %s AND mc.is_deleted = false
        LIMIT 1
        """,
        ("ulasbayram61@gmail.com", "kadeh_icecekler"),
    )
    stage_cat = cur.fetchone()[0]
    cur.execute(
        """
        UPDATE tbl_menu_category
        SET image_url = %s, updated_at = %s
        WHERE id = %s
        """,
        (cover_url, now(), stage_cat),
    )
    conn.commit()
    print("STAGE cover", stage_cat, cover_url)
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
