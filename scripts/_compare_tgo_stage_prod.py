import base64
import hashlib
import json
import os
import urllib.error
import urllib.request

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


def encrypt(plaintext: str) -> str:
    key = normalize_key("")
    iv = os.urandom(12)
    ct = AESGCM(key).encrypt(iv, plaintext.encode("utf-8"), None)
    return base64.b64encode(iv + ct).decode("ascii")


def decrypt(ciphertext: str) -> str:
    key = normalize_key("")
    raw = base64.b64decode(ciphertext)
    return AESGCM(key).decrypt(raw[:12], raw[12:], None).decode("utf-8")


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
    print(f"=== {name} ===")
    for r in cur.fetchall():
        key = decrypt(r[8]) if r[8] else None
        secret = decrypt(r[9]) if r[9] else None
        print(
            dict(
                id=r[0],
                user=r[1],
                branch=r[2],
                seller=r[3],
                restaurant=r[4],
                name=r[5],
                status=r[6],
                last_error=r[7],
                key_ok=key == API_KEY,
                secret_ok=secret == API_SECRET,
                key_last4=(key or "")[-4:],
            )
        )
    cur.close()
    c.close()


def probe_tgo():
    token = base64.b64encode(f"{API_KEY}:{API_SECRET}".encode()).decode()
    url = f"https://api.tgoapis.com/integrator/store/meal/suppliers/{SELLER_ID}/stores"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Basic {token}",
            "User-Agent": f"{SELLER_ID} - AlgoryQR",
            "Accept": "application/json",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            print("TGO stores OK", resp.status, body[:800])
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print("TGO stores HTTP", e.code, body[:800])
    except Exception as e:
        print("TGO stores ERR", e)


if __name__ == "__main__":
    dump("STAGE", STAGE)
    dump("PROD", PROD)
    print("---")
    probe_tgo()
