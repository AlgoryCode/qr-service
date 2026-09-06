from __future__ import annotations

import json
import mimetypes
import ssl
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

import psycopg2

API = "https://prod.qrapi.algorycode.com"
ADMIN_EMAIL = "admin@example.com"
ADMIN_PASSWORD = "Admin123!"
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
PREFERRED_PRODUCT_BY_SLUG = {
    "corbalar": "%bal%k%orb%",
    "etler": "%pirzola%",
}


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def api_json(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as resp:
        return json.loads(resp.read())


def login_token() -> str:
    admin = api_json(
        "POST",
        "/dashboard/auth/login",
        body={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD},
    )
    impersonated = api_json(
        "POST",
        f"/admin/users/{USER_ID}/impersonate",
        token=admin["accessToken"],
        body={},
    )
    return impersonated["accessToken"]


def download_image(url: str) -> tuple[bytes, str]:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as resp:
        data = resp.read()
        content_type = (resp.headers.get("Content-Type") or "image/jpeg").split(";")[0].strip()
    ext = mimetypes.guess_extension(content_type) or ".jpg"
    if "png" in content_type:
        ext = ".png"
    elif "webp" in content_type:
        ext = ".webp"
    elif "jpeg" in content_type or "jpg" in content_type:
        ext = ".jpg"
    return data, ext


def upload_category_cover(token: str, category_id: int, image_bytes: bytes, filename: str) -> str:
    content_type = mimetypes.guess_type(filename)[0] or "image/jpeg"
    boundary = f"----AlgoryBoundary{uuid.uuid4().hex}"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode() + image_bytes + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        f"{API}/menu/{MENU_ID}/categories/{category_id}/cover",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=90, context=SSL_CTX) as resp:
        payload = json.loads(resp.read())
    cover_url = payload.get("imageUrl") or payload.get("image_url")
    if not cover_url:
        raise RuntimeError(f"cover upload missing imageUrl: {payload}")
    return cover_url


def pick_product_image(cur, category_id: int, slug: str) -> tuple[str, str] | None:
    preferred = PREFERRED_PRODUCT_BY_SLUG.get(slug)
    if preferred:
        cur.execute(
            """
            SELECT p.name, p.image_url
            FROM tbl_menu_products p
            JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
            WHERE p.menu_id = %s
              AND sc.menu_category_id = %s
              AND p.is_deleted = false
              AND p.image_url IS NOT NULL
              AND p.image_url <> ''
              AND p.name ILIKE %s
            ORDER BY p.sort_order, p.product_id
            LIMIT 1
            """,
            (MENU_ID, category_id, preferred),
        )
        row = cur.fetchone()
        if row:
            return row[0], row[1]

    cur.execute(
        """
        SELECT p.name, p.image_url
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        WHERE p.menu_id = %s
          AND sc.menu_category_id = %s
          AND p.is_deleted = false
          AND p.image_url IS NOT NULL
          AND p.image_url <> ''
        ORDER BY p.sort_order, p.product_id
        LIMIT 1
        """,
        (MENU_ID, category_id),
    )
    row = cur.fetchone()
    if not row:
        return None
    return row[0], row[1]


def main() -> None:
    token = login_token()
    conn = psycopg2.connect(**PROD)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT id, slug, name, image_url
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (MENU_ID,),
    )
    categories = cur.fetchall()

    ok = 0
    skip = 0
    fail = 0
    for category_id, slug, name, _existing in categories:
        picked = pick_product_image(cur, category_id, slug)
        if picked is None:
            print(f"SKIP {slug} ({category_id}): no product image")
            skip += 1
            continue
        product_name, image_url = picked
        try:
            image_bytes, ext = download_image(image_url)
            cover_url = upload_category_cover(
                token,
                category_id,
                image_bytes,
                f"{slug}-cover{ext}",
            )
            ok += 1
            print(f"OK {name} <- {product_name} -> {cover_url[:90]}")
        except urllib.error.HTTPError as exc:
            fail += 1
            detail = exc.read().decode(errors="replace")
            print(f"FAIL {slug} HTTP {exc.code}: {detail[:200]}")
            try:
                cur.execute(
                    """
                    UPDATE tbl_menu_category
                    SET image_url = %s, updated_at = %s
                    WHERE id = %s
                    """,
                    (image_url, now(), category_id),
                )
                conn.commit()
                print(f"FALLBACK {slug} -> product url")
            except Exception as exc2:  # noqa: BLE001
                print(f"FALLBACK FAIL {slug}: {exc2}")
        except Exception as exc:  # noqa: BLE001
            fail += 1
            print(f"FAIL {slug}: {exc}")

    cur.close()
    conn.close()
    print(f"done ok={ok} skip={skip} fail={fail}")


if __name__ == "__main__":
    main()
