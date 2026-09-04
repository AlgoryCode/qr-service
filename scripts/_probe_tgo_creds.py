import base64
import hashlib
import json
import os
import urllib.request
import urllib.error

import psycopg2
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

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

SELLER_ID = "6730477"
API_KEY = "822x8W0M7oTMPHH88hkR"
API_SECRET = "DUbk9Fhnjq56ZKW1Qb1Y"
REF_CODE = "124dcf69-eac9-4f8e-858b-7814dafa7e17"


def normalize_key(raw: str) -> bytes:
    if not raw:
        return hashlib.sha256(b"algoryqr-tgo-local-dev-key").digest()
    try:
        decoded = base64.b64decode(raw.strip(), validate=True)
        if len(decoded) in (16, 24, 32):
            return decoded
    except Exception:
        pass
    return hashlib.sha256(raw.encode("utf-8")).digest()


def encrypt(plaintext: str, key_raw: str) -> str:
    key = normalize_key(key_raw)
    iv = os.urandom(12)
    aesgcm = AESGCM(key)
    ct = aesgcm.encrypt(iv, plaintext.encode("utf-8"), None)
    return base64.b64encode(iv + ct).decode("ascii")


def decrypt(ciphertext: str, key_raw: str) -> str:
    key = normalize_key(key_raw)
    raw = base64.b64decode(ciphertext)
    iv, ct = raw[:12], raw[12:]
    return AESGCM(key).decrypt(iv, ct, None).decode("utf-8")


def dump(name, cfg):
    c = psycopg2.connect(**cfg)
    cur = c.cursor()
    cur.execute(
        """
        SELECT id, user_id, branch_id, seller_id, restaurant_id, restaurant_name,
               status, last_error, api_key_encrypted, api_secret_encrypted
        FROM tbl_trendyol_go_connection
        ORDER BY id
        """
    )
    print(f"\n=== {name} connections ===")
    for r in cur.fetchall():
        print("id", r[0], "user", r[1], "branch", r[2], "seller", r[3])
        print("  restaurant", r[4], r[5], "status", r[6])
        print("  last_error", r[7])
        print("  key_enc_prefix", (r[8] or "")[:40])
        print("  secret_enc_prefix", (r[9] or "")[:40])
        for label, enc in [("key", r[8]), ("secret", r[9])]:
            if not enc:
                continue
            for kname, kraw in [
                ("empty/default", ""),
                ("env", os.environ.get("TGO_CREDENTIALS_ENCRYPT_KEY", "")),
            ]:
                try:
                    plain = decrypt(enc, kraw)
                    print(f"  decrypt {label} with {kname}: OK len={len(plain)} last4={plain[-4:]}")
                except Exception as e:
                    print(f"  decrypt {label} with {kname}: FAIL {type(e).__name__}")
    cur.close()
    c.close()


def try_tgo_restaurants():
    token = base64.b64encode(f"{API_KEY}:{API_SECRET}".encode()).decode()
    urls = [
        f"https://api.tgoapis.com/restaurant-api/restaurants/{SELLER_ID}",
        f"https://api.tgoapis.com/meal/supplier/restaurants/{SELLER_ID}",
        f"https://api.tgoapis.com/integrator/restaurant/restaurants/{SELLER_ID}",
    ]
    # read paths from yaml mentally - check properties
    headers = {
        "Authorization": f"Basic {token}",
        "User-Agent": f"{SELLER_ID} - AlgoryQR",
        "Accept": "application/json",
    }
    for url in urls:
        req = urllib.request.Request(url, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                body = resp.read().decode("utf-8", errors="replace")
                print("OK", url, resp.status, body[:500])
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            print("HTTP", e.code, url, body[:300])
        except Exception as e:
            print("ERR", url, e)


def main():
    dump("STAGE", STAGE)
    dump("PROD", PROD)
    print("\n=== TGO API probe ===")
    try_tgo_restaurants()


if __name__ == "__main__":
    main()
