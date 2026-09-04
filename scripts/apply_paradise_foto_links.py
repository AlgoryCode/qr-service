from __future__ import annotations

import json
import mimetypes
import re
import ssl
import time
import unicodedata
import urllib.error
import urllib.request
import uuid
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import openpyxl
import psycopg2

EXCEL = Path(r"C:\Users\Tarik\Downloads\menu_urun_foto_linkleri (1).xlsx")
API = "https://prod.qrapi.algorycode.com"
ADMIN_EMAIL = "admin@example.com"
ADMIN_PASSWORD = "Admin123!"
TARGET_EMAIL = "ulasbayram61@gmail.com"
PROD_MENU_ID = 17
PROD_USER_ID = 21

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

SSL_CTX = ssl.create_default_context()
SSL_UNVERIFIED = ssl._create_unverified_context()
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
STATE = Path(__file__).with_name("_paradise_foto_state.json")


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", (text or "").lower())
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.replace("ı", "i").replace("İ", "i")
    return re.sub(r"[^a-z0-9]+", "", text)


def api_json(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as resp:
        return json.loads(resp.read())


def login_token() -> str:
    admin = api_json("POST", "/dashboard/auth/login", body={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    impersonated = api_json(
        "POST",
        f"/admin/users/{PROD_USER_ID}/impersonate",
        token=admin["accessToken"],
        body={},
    )
    return impersonated["accessToken"]


def to_jpeg_if_needed(data: bytes, ext: str) -> tuple[bytes, str]:
    if ext.lower() in {".jpg", ".jpeg", ".png"}:
        return data, ext if ext.lower() != ".jpeg" else ".jpg"
    try:
        from io import BytesIO

        from PIL import Image

        image = Image.open(BytesIO(data))
        if image.mode not in ("RGB", "L"):
            image = image.convert("RGB")
        out = BytesIO()
        image.save(out, format="JPEG", quality=90)
        return out.getvalue(), ".jpg"
    except Exception:
        return data, ".jpg"


def download_image(url: str) -> tuple[bytes, str]:
    last_error: Exception | None = None
    for ctx in (SSL_CTX, SSL_UNVERIFIED):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "image/*,*/*"})
            with urllib.request.urlopen(req, timeout=60, context=ctx) as resp:
                data = resp.read()
                content_type = resp.headers.get("Content-Type", "image/jpeg").split(";")[0].strip()
            if len(data) < 500:
                raise RuntimeError(f"image too small ({len(data)} bytes)")
            ext = mimetypes.guess_extension(content_type) or ".jpg"
            if ext == ".jpe":
                ext = ".jpg"
            if "webp" in content_type or url.lower().endswith(".webp"):
                ext = ".webp"
            if "png" in content_type:
                ext = ".png"
            return to_jpeg_if_needed(data, ext)
        except Exception as exc:  # noqa: BLE001
            last_error = exc
    raise RuntimeError(f"download failed: {last_error}")


def upload_product_image(token: str, image_bytes: bytes, filename: str) -> str:
    boundary = f"----AlgoryBoundary{uuid.uuid4().hex}"
    content_type = mimetypes.guess_type(filename)[0] or "image/jpeg"
    if filename.lower().endswith(".webp"):
        content_type = "image/webp"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode() + image_bytes + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        f"{API}/menu/{PROD_MENU_ID}/products/images",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=90, context=SSL_CTX) as resp:
            payload = json.loads(resp.read())
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")
        raise RuntimeError(f"upload HTTP {exc.code}: {detail[:300]}") from exc
    image_url = payload.get("imageUrl") or payload.get("image_url")
    if not image_url:
        raise RuntimeError(f"upload missing imageUrl: {payload}")
    return image_url


def upload_category_cover(token: str, category_id: int, image_bytes: bytes, filename: str) -> str:
    boundary = f"----AlgoryBoundary{uuid.uuid4().hex}"
    content_type = mimetypes.guess_type(filename)[0] or "image/jpeg"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode() + image_bytes + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        f"{API}/menu/{PROD_MENU_ID}/categories/{category_id}/cover",
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
    image_url = payload.get("imageUrl") or payload.get("image_url")
    if not image_url:
        raise RuntimeError(f"cover upload missing imageUrl: {payload}")
    return image_url


def load_excel_rows() -> list[tuple[str, str, str]]:
    wb = openpyxl.load_workbook(EXCEL, data_only=True)
    rows: list[tuple[str, str, str]] = []
    for kategori, _alt, urun, link in list(wb.active.iter_rows(values_only=True))[1:]:
        if not urun or not link:
            continue
        rows.append((str(kategori or ""), str(urun).strip(), str(link).strip()))
    return rows


def load_products(cfg: dict, menu_id: int) -> list[tuple[int, str, int, str, str | None]]:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT p.product_id, p.name, mc.id, mc.slug, p.image_url
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category mc ON mc.id = sc.menu_category_id
        WHERE p.menu_id = %s AND p.is_deleted = false AND mc.is_deleted = false
        """,
        (menu_id,),
    )
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return rows


def match_product(
    name: str, by_norm: dict[str, tuple[int, str, int, str, str | None]]
) -> tuple[int, str, int, str, str | None] | None:
    key = normalize(name)
    if key in by_norm:
        return by_norm[key]
    variants = [
        normalize(re.sub(r"\s*/\s*", " ", name)),
        normalize(re.sub(r"\s+\d+\s*gr.*$", "", name, flags=re.I)),
        normalize(re.sub(r"\s+\d+\s*cl.*$", "", name, flags=re.I)),
    ]
    for variant in variants:
        if variant and variant in by_norm:
            return by_norm[variant]
    for pn, product in by_norm.items():
        if key and (key in pn or pn in key) and abs(len(pn) - len(key)) <= 8:
            return product
    return None


def update_product_image(cfg: dict, product_id: int, image_url: str) -> None:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    cur.execute(
        """
        UPDATE tbl_menu_products
        SET image_url = %s, updated_at = %s
        WHERE product_id = %s
        """,
        (image_url, now(), product_id),
    )
    conn.commit()
    cur.close()
    conn.close()


def update_category_cover_url(cfg: dict, category_id: int, image_url: str, image_key: str | None = None) -> None:
    conn = psycopg2.connect(**cfg)
    cur = conn.cursor()
    if image_key:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET image_url = %s, image_key = %s, updated_at = %s
            WHERE id = %s
            """,
            (image_url, image_key, now(), category_id),
        )
    else:
        cur.execute(
            """
            UPDATE tbl_menu_category
            SET image_url = %s, updated_at = %s
            WHERE id = %s
            """,
            (image_url, now(), category_id),
        )
    conn.commit()
    cur.close()
    conn.close()


def resolve_stage_menu_id() -> int | None:
    conn = psycopg2.connect(**STAGE)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT m.menu_id
        FROM tbl_menu m
        JOIN tbl_user u ON u.id = m.user_id
        WHERE u.email = %s AND m.is_deleted = false
        ORDER BY m.menu_id
        LIMIT 1
        """,
        (TARGET_EMAIL,),
    )
    row = cur.fetchone()
    cur.close()
    conn.close()
    return row[0] if row else None


def main() -> None:
    excel_rows = load_excel_rows()
    print(f"excel rows with links: {len(excel_rows)}")

    prod_products = load_products(PROD, PROD_MENU_ID)
    prod_by_norm = {normalize(row[1]): row for row in prod_products}
    print(f"prod products={len(prod_products)}")

    stage_menu_id = resolve_stage_menu_id()
    stage_by_norm: dict[str, tuple[int, str, int, str, str | None]] = {}
    if stage_menu_id is not None:
        stage_products = load_products(STAGE, stage_menu_id)
        stage_by_norm = {normalize(row[1]): row for row in stage_products}
        print(f"stage menu={stage_menu_id} products={len(stage_products)}")

    state: dict = {}
    if STATE.exists():
        state = json.loads(STATE.read_text(encoding="utf-8"))
    done: dict[str, str] = state.get("done", {})

    token = login_token()
    token_at = time.time()

    ok = 0
    fail = 0
    skipped = 0
    category_first_image: dict[int, tuple[bytes, str, str]] = {}
    category_slug: dict[int, str] = {}

    for idx, (_excel_cat, name, link) in enumerate(excel_rows, start=1):
        if time.time() - token_at > 500:
            token = login_token()
            token_at = time.time()
            print("token refreshed")

        prod = match_product(name, prod_by_norm)
        if prod is None:
            print(f"MISS {name}")
            fail += 1
            continue

        product_id, product_name, category_id, cat_slug, _old = prod
        category_slug[category_id] = cat_slug
        state_key = str(product_id)

        try:
            if state_key in done and done[state_key].startswith("https://images.algorycode.com/"):
                image_url = done[state_key]
                skipped += 1
                image_bytes = None
                ext = ".jpg"
            else:
                image_bytes, ext = download_image(link)
                safe = re.sub(r"[^a-zA-Z0-9_-]+", "_", product_name)[:40] or "product"
                image_url = upload_product_image(token, image_bytes, f"{safe}{ext}")
                update_product_image(PROD, product_id, image_url)
                done[state_key] = image_url
                ok += 1
                print(f"OK [{ok}] {product_name} -> {image_url[:80]}")

            if category_id not in category_first_image:
                if image_bytes is None:
                    image_bytes, ext = download_image(image_url if image_url.startswith("http") else link)
                category_first_image[category_id] = (image_bytes, ext, image_url)

            stage_prod = match_product(name, stage_by_norm) if stage_by_norm else None
            if stage_prod is not None:
                update_product_image(STAGE, stage_prod[0], image_url)

        except Exception as exc:  # noqa: BLE001
            fail += 1
            print(f"FAIL {name}: {exc}")

        if idx % 10 == 0:
            STATE.write_text(json.dumps({"done": done}, ensure_ascii=False, indent=2), encoding="utf-8")

    STATE.write_text(json.dumps({"done": done}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"products ok={ok} skipped={skipped} fail={fail}")

    cover_ok = 0
    for category_id, (image_bytes, ext, image_url) in category_first_image.items():
        slug = category_slug.get(category_id, str(category_id))
        try:
            if time.time() - token_at > 500:
                token = login_token()
                token_at = time.time()
            cover_url = upload_category_cover(token, category_id, image_bytes, f"{slug}-cover{ext}")
            cover_ok += 1
            print(f"COVER {slug} ({category_id}) -> {cover_url[:80]}")

            if stage_menu_id is not None:
                conn = psycopg2.connect(**STAGE)
                cur = conn.cursor()
                cur.execute(
                    """
                    SELECT id FROM tbl_menu_category
                    WHERE menu_id = %s AND slug = %s AND is_deleted = false
                    """,
                    (stage_menu_id, slug),
                )
                row = cur.fetchone()
                cur.close()
                conn.close()
                if row:
                    update_category_cover_url(STAGE, row[0], cover_url)
        except Exception as exc:  # noqa: BLE001
            print(f"COVER FAIL {slug}: {exc}")
            try:
                update_category_cover_url(PROD, category_id, image_url)
                print(f"COVER FALLBACK {slug} -> product url")
            except Exception as exc2:  # noqa: BLE001
                print(f"COVER FALLBACK FAIL {slug}: {exc2}")

    print(f"covers uploaded: {cover_ok}/{len(category_first_image)}")


if __name__ == "__main__":
    main()
