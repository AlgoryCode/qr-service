import base64
import hashlib
import os

import psycopg2
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

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
RESTAURANT_ID = "463639"
RESTAURANT_NAME = "Mexican Döner"
USER_ID = 1
BRANCH_ID = 7


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


conn = psycopg2.connect(**PROD)
conn.autocommit = False
cur = conn.cursor()

cur.execute(
    """
    SELECT id, user_id, branch_id, seller_id, restaurant_id, restaurant_name,
           status, api_key_encrypted, api_secret_encrypted
    FROM tbl_trendyol_go_connection
    WHERE user_id = %s
    ORDER BY id
    """,
    (USER_ID,),
)
rows = cur.fetchall()
print("before:", [(r[0], r[1], r[2], r[3], r[4], r[5], r[6]) for r in rows])

if not rows:
    cur.execute(
        """
        INSERT INTO tbl_trendyol_go_connection (
          user_id, branch_id, seller_id, api_key_encrypted, api_secret_encrypted,
          restaurant_id, restaurant_name, status, created_at, updated_at
        ) VALUES (
          %s, %s, %s, %s, %s, %s, %s, 'CONNECTED', NOW(), NOW()
        )
        RETURNING id
        """,
        (
            USER_ID,
            BRANCH_ID,
            SELLER_ID,
            encrypt(API_KEY),
            encrypt(API_SECRET),
            RESTAURANT_ID,
            RESTAURANT_NAME,
        ),
    )
    print("inserted id", cur.fetchone()[0])
else:
    row = rows[0]
    conn_id = row[0]
    current_key = decrypt(row[7])
    current_secret = decrypt(row[8])
    print("current key match:", current_key == API_KEY)
    print("current secret match:", current_secret == API_SECRET)
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
            updated_at = NOW()
        WHERE id = %s
        """,
        (
            SELLER_ID,
            encrypt(API_KEY),
            encrypt(API_SECRET),
            RESTAURANT_ID,
            RESTAURANT_NAME,
            BRANCH_ID,
            conn_id,
        ),
    )
    print("updated id", conn_id)

cur.execute(
    """
    SELECT id, user_id, branch_id, seller_id, restaurant_id, restaurant_name, status,
           api_key_encrypted, api_secret_encrypted
    FROM tbl_trendyol_go_connection
    WHERE user_id = %s
    """,
    (USER_ID,),
)
final = cur.fetchone()
print(
    "after:",
    final[0],
    final[1],
    final[2],
    final[3],
    final[4],
    final[5],
    final[6],
)
print("verify key:", decrypt(final[7]) == API_KEY)
print("verify secret:", decrypt(final[8]) == API_SECRET)

conn.commit()
cur.close()
conn.close()
print("committed")
