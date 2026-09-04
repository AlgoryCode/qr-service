import base64
import hashlib
import os

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

SELLER_ID = "6730477"
API_KEY = "822x8W0M7oTMPHH88hkR"
API_SECRET = "DUbk9Fhnjq56ZKW1Qb1Y"
RESTAURANT_ID = "463639"
RESTAURANT_NAME = "Mexican Döner"
USER_ID = 22
BRANCH_ID = 1


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


conn = psycopg2.connect(**STAGE)
conn.autocommit = False
cur = conn.cursor()

cur.execute(
    """
    UPDATE tbl_trendyol_go_connection
    SET seller_id = %s,
        api_key_encrypted = %s,
        api_secret_encrypted = %s,
        restaurant_id = %s,
        restaurant_name = %s,
        status = 'CONNECTED',
        last_error = NULL,
        branch_id = %s,
        updated_at = NOW(),
        last_synced_at = NOW()
    WHERE user_id = %s
    RETURNING id, status, restaurant_name, seller_id, branch_id
    """,
    (
        SELLER_ID,
        encrypt(API_KEY),
        encrypt(API_SECRET),
        RESTAURANT_ID,
        RESTAURANT_NAME,
        BRANCH_ID,
        USER_ID,
    ),
)
print("updated:", cur.fetchall())
conn.commit()
cur.close()
conn.close()
