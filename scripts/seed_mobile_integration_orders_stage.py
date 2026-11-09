#!/usr/bin/env python3
"""Demo Uber Eats Trendyol Go and Yemeksepeti connections with platform orders for the mobile test merchant (86) on stage.

Lets the business app's Entegrasyonlar screen be tried without real platform accounts. The connections hold fake,
encrypted keys, so order actions (accept, reject, ready) fail at the platform and background polling that decrypts
them with a different key marks the connection ERROR. Orders are marked external_order_id 'SEED-INT-…' and
replaced on rerun; connections are reused.

Keys are encrypted the way UberEatsCredentialEncryptor / YemekSepetiCredentialEncryptor do with no encrypt key
configured (local default), which is what a locally started qr-service uses.
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
from datetime import datetime, timedelta
from decimal import Decimal
from random import Random

import psycopg2
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MERCHANT_ID = 86
PREFIX = "SEED-INT-"

UBER_EATS = {
    "branch_id": 41,
    "seller_id": "DEMO-SELLER-86",
    "restaurant_id": "DEMO-TG-41",
    "restaurant_name": "Mobil Test Kadıköy",
    "key": "algoryqr-tgo-local-dev-key",
    "menu": [("Izgara Köfte", 240), ("Adana Kebap", 280), ("Tavuk Şiş", 210), ("Künefe", 160), ("Ayran", 40),
             ("Limonata", 70), ("Mercimek Çorbası", 95)],
    # (status, minutes ago); the newest are waiting for an answer.
    "orders": [("Created", 2), ("Created", 6), ("Picking", 14), ("Picking", 22), ("Invoiced", 31), ("Shipped", 48),
               ("Delivered", 95), ("Delivered", 140), ("Delivered", 260), ("UnSupplied", 300), ("Delivered", 1500),
               ("Cancelled", 1700), ("Delivered", 1900)],
}
YEMEKSEPETI = {
    "branch_id": 42,
    "chain_id": "DEMO-CHAIN-86",
    "vendor_id": "DEMO-YS-42",
    "vendor_name": "Mobil Test Beşiktaş",
    "key": "algoryqr-yemeksepeti-local-dev-key",
    "menu": [("Serpme Kahvaltı", 450), ("Menemen", 140), ("Kaşarlı Tost", 110), ("Latte", 95), ("Cheesecake", 170),
             ("Filtre Kahve", 85)],
    "orders": [("RECEIVED", 4), ("ACCEPTED", 18), ("READY_FOR_PICKUP", 35), ("PICKED_UP", 52), ("DELIVERED", 120),
               ("DELIVERED", 210), ("CANCELLED", 330), ("DELIVERED", 1450), ("DELIVERED", 1600)],
}
CUSTOMERS = [
    ("Zeynep K.", "0532 *** ** 41", "Caferağa Mah. Moda Cad. No:18 D:3, Kadıköy"),
    ("Burak T.", "0541 *** ** 07", "Osmanağa Mah. Bahariye Cad. No:52 D:7, Kadıköy"),
    ("Elif S.", "0505 *** ** 93", "Sinanpaşa Mah. Ihlamurdere Cad. No:9, Beşiktaş"),
    ("Can D.", "0533 *** ** 26", "Türkali Mah. Nüzhetiye Cad. No:31 D:2, Beşiktaş"),
    ("Merve A.", "0507 *** ** 58", "Rasimpaşa Mah. Söğütlüçeşme Cad. No:4, Kadıköy"),
]
NOTES = [None, None, "Zili çalmayın, kapıya bırakın", None, "Acısız olsun lütfen", None, "Peçete ekleyin"]


def connect():
    return psycopg2.connect(
        host="185.184.210.52",
        port=5433,
        dbname="algoryqrdb-stage",
        user="postgres",
        password="postgres_stage",
    )


def encrypt(default_key: str, plaintext: str) -> str:
    """AES/GCM with a random 12-byte IV prepended, Base64 — same layout as the Java encryptors."""
    key = hashlib.sha256(default_key.encode()).digest()
    iv = os.urandom(12)
    return base64.b64encode(iv + AESGCM(key).encrypt(iv, plaintext.encode(), None)).decode()


def ensure_uber_eats(cur, now) -> int:
    spec = UBER_EATS
    cur.execute("SELECT id FROM ubereats_connections WHERE merchant_id = %s", (MERCHANT_ID,))
    row = cur.fetchone()
    values = (
        encrypt(spec["key"], "demo-api-key-0086"), encrypt(spec["key"], "demo-api-secret-0086"), spec["branch_id"],
        spec["restaurant_id"], spec["restaurant_name"], spec["seller_id"], now,
    )
    if row:
        cur.execute(
            """
            UPDATE ubereats_connections SET api_key_encrypted = %s, api_secret_encrypted = %s, branch_id = %s,
                restaurant_id = %s, restaurant_name = %s, seller_id = %s, status = 'CONNECTED', last_error = NULL,
                last_synced_at = %s, updated_at = now()
            WHERE id = %s
            """,
            values + (row[0],),
        )
        return row[0]
    cur.execute(
        """
        INSERT INTO ubereats_connections (
            api_key_encrypted, api_secret_encrypted, branch_id, restaurant_id, restaurant_name, seller_id,
            last_synced_at, status, created_at, updated_at, merchant_id
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, 'CONNECTED', now(), now(), %s)
        RETURNING id
        """,
        values + (MERCHANT_ID,),
    )
    return cur.fetchone()[0]


def ensure_yemeksepeti(cur, now) -> int:
    spec = YEMEKSEPETI
    cur.execute("SELECT id FROM yemeksepeti_connections WHERE merchant_id = %s", (MERCHANT_ID,))
    row = cur.fetchone()
    values = (
        spec["chain_id"], spec["vendor_id"], spec["vendor_name"], encrypt(spec["key"], "demo-client-id-0086"),
        encrypt(spec["key"], "demo-client-secret-0086"), encrypt(spec["key"], "demo-webhook-secret-0086"),
        now, spec["branch_id"],
    )
    if row:
        cur.execute(
            """
            UPDATE yemeksepeti_connections SET chain_id = %s, vendor_id = %s, vendor_name = %s,
                client_id_encrypted = %s, client_secret_encrypted = %s, webhook_secret_encrypted = %s,
                last_synced_at = %s, branch_id = %s, status = 'CONNECTED', last_error = NULL, updated_at = now()
            WHERE id = %s
            """,
            values + (row[0],),
        )
        return row[0]
    cur.execute(
        """
        INSERT INTO yemeksepeti_connections (
            chain_id, vendor_id, vendor_name, client_id_encrypted, client_secret_encrypted, webhook_secret_encrypted,
            last_synced_at, branch_id, status, created_at, updated_at, merchant_id
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 'CONNECTED', now(), now(), %s)
        RETURNING id
        """,
        values + (MERCHANT_ID,),
    )
    return cur.fetchone()[0]


def build_orders(rng, spec, now, uber_eats: bool):
    rows = []
    for index, (status, minutes_ago) in enumerate(spec["orders"]):
        created = now - timedelta(minutes=minutes_ago)
        picked = rng.sample(spec["menu"], rng.randint(1, 3))
        items = []
        total = Decimal("0")
        for name, price in picked:
            qty = rng.choice([1, 1, 2])
            total += Decimal(price) * qty
            # qr-service rebuilds Uber Eats items from raw_payload when no item has a detail, and the demo payload has none.
            items.append({"productId": None, "productName": name, "quantity": qty, "unitPrice": float(price),
                          "options": None, "detail": "1 porsiyon"})
        customer = CUSTOMERS[index % len(CUSTOMERS)]
        number = f"{rng.randint(100000, 999999)}"
        cash = rng.random() < 0.25
        if uber_eats:
            payload = {"orderCode": number, "deliveryTypeText": "Uber Eats kuryesi",
                       "paymentMethodText": "Kapıda nakit" if cash else "Online ödeme"}
        else:
            payload = {"order_code": number, "order_type": "Teslimat",
                       "payment": {"type": "Kapıda nakit" if cash else "Online ödeme"}}
        rows.append((
            f"{PREFIX}{'UE' if uber_eats else 'YS'}-{number}", status, total, customer[0], customer[1], customer[2],
            NOTES[index % len(NOTES)], created, json.dumps(items, ensure_ascii=False), json.dumps(payload, ensure_ascii=False),
        ))
    return rows


def insert_orders(cur, table, connection_id, rows):
    cur.execute(f"DELETE FROM {table} WHERE connection_id = %s AND external_order_id LIKE %s", (connection_id, PREFIX + "%"))
    for row in rows:
        cur.execute(
            f"""
            INSERT INTO {table} (
                connection_id, external_order_id, package_status, total_amount, currency, customer_name,
                customer_phone, delivery_address, note, package_created_at, items_json, raw_payload,
                created_at, updated_at
            ) VALUES (%s, %s, %s, %s, 'TRY', %s, %s, %s, %s, %s, %s::jsonb, %s::jsonb, %s, %s)
            """,
            (connection_id,) + row + (row[7], row[7]),
        )


def main():
    rng = Random(86)
    now = datetime.now().replace(microsecond=0)
    conn = connect()
    cur = conn.cursor()
    uber_eats_id = ensure_uber_eats(cur, now)
    yemeksepeti_id = ensure_yemeksepeti(cur, now)
    insert_orders(cur, "ubereats_orders", uber_eats_id, build_orders(rng, UBER_EATS, now, uber_eats=True))
    insert_orders(cur, "yemeksepeti_orders", yemeksepeti_id, build_orders(rng, YEMEKSEPETI, now, uber_eats=False))
    conn.commit()
    print(f"Uber Eats Trendyol Go connection {uber_eats_id}: {len(UBER_EATS['orders'])} orders")
    print(f"Yemeksepeti connection {yemeksepeti_id}: {len(YEMEKSEPETI['orders'])} orders")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
