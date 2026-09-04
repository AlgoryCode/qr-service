from __future__ import annotations

import json
import mimetypes
import ssl
import urllib.error
import urllib.request
import uuid
from pathlib import Path

import psycopg2

API = "https://prod.qrapi.algorycode.com"
ADMIN_EMAIL = "admin@example.com"
ADMIN_PASSWORD = "Admin123!"
MENU_ID = 16
USER_ID = 20
ASSETS = Path(r"C:\Users\Tarik\.cursor\projects\c-Users-Tarik-Desktop-Services\assets")

COVER_FILES = {
    "corbalar": ASSETS / "aya-prod-cover-corbalar.png",
    "soguk_mezeler": ASSETS / "aya-prod-cover-soguk-mezeler.png",
    "salatalar": ASSETS / "aya-prod-cover-salatalar.png",
    "ara_sicaklar": ASSETS / "aya-prod-cover-ara-sicaklar.png",
    "baliklar": ASSETS / "aya-prod-cover-baliklar.png",
    "deniz_urunleri": ASSETS / "aya-prod-cover-deniz-urunleri.png",
    "etler": ASSETS / "aya-prod-cover-etler.png",
    "makarnalar": ASSETS / "aya-prod-cover-makarnalar.png",
    "tatlilar": ASSETS / "aya-prod-cover-tatlilar.png",
    "icecekler": ASSETS / "aya-prod-cover-icecekler.png",
}

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
)

SSL_CTX = ssl.create_default_context()


def api_json(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30, context=SSL_CTX) as resp:
        return json.loads(resp.read())


def upload_cover(token: str, category_id: int, image_path: Path) -> dict:
    boundary = f"----AlgoryBoundary{uuid.uuid4().hex}"
    content_type = mimetypes.guess_type(image_path.name)[0] or "image/png"
    file_bytes = image_path.read_bytes()
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{image_path.name}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode() + file_bytes + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        f"{API}/menu/{MENU_ID}/categories/{category_id}/cover",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as resp:
        return json.loads(resp.read())


def main() -> None:
    admin = api_json("POST", "/dashboard/auth/login", body={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    impersonated = api_json("POST", f"/admin/users/{USER_ID}/impersonate", token=admin["accessToken"])
    token = impersonated["accessToken"]

    conn = psycopg2.connect(**PROD)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT id, slug FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order
        """,
        (MENU_ID,),
    )
    categories = cur.fetchall()
    cur.close()
    conn.close()

    for category_id, slug in categories:
        image_path = COVER_FILES.get(slug)
        if not image_path or not image_path.exists():
            print(f"SKIP {slug}: missing {image_path}")
            continue
        try:
            result = upload_cover(token, category_id, image_path)
            print(f"OK {slug} ({category_id}) -> {result.get('imageUrl', '')[:90]}")
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode(errors="replace")
            print(f"FAIL {slug} ({category_id}) HTTP {exc.code}: {detail[:200]}")


if __name__ == "__main__":
    main()
