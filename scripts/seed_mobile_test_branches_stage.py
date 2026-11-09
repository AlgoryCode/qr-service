#!/usr/bin/env python3
"""Two extra branches with 90 days of demo sales for the mobile test merchant (86) on the stage database.

Each branch gets its own QR menu, catalog, tables and staff, then closed bills with payments, tips and waiter
commissions up to now, so the business app's reports have something to compare between branches.
Sales rows are marked note = SEED_MOBILE_BRANCHES and replaced on rerun; branches, menus and catalog are reused.
"""

from __future__ import annotations

import base64
import json
import secrets
import uuid
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP
from random import Random

import psycopg2
from psycopg2.extras import Json, execute_values

SEED = "SEED_MOBILE_BRANCHES"
MERCHANT_ID = 86
ORDER_FLOW_ID = 1
WAITER_ROLE_ID = 7
KITCHEN_ROLE_ID = 8
DAYS = 90
PASSWORD_HASH = "$2b$08$u1uoB/4zar3qJgmKUtsS0OeOOzWXaFtrj/SX12Lm6dl2KLencDqoK"
NUTRITION = {
    "fat": 8, "salt": 1, "basis": "PER_100G", "fibre": 2, "starch": None, "sugars": None, "polyols": None,
    "protein": 12, "energyKj": 836, "energyKcal": 200, "carbohydrate": 20, "saturatedFat": None,
    "otherNutrients": None, "vitaminsAndMinerals": None,
}

# Catalog: category slug -> (name, skip_kitchen, [(sub slug, sub name, [(product, price)])])
BRANCHES = [
    {
        "name": "Kadıköy Şubesi",
        "address": "Moda Cad. No:12, Kadıköy",
        "menu": "Mobil Test Kadıköy",
        "tables": 10,
        "bills_per_day": [22, 24, 24, 26, 34, 42, 36],
        "card_share": 0.74,
        "qr_share": 0.3,
        "hours": [12] * 6 + [13] * 10 + [14] * 7 + [15] * 4 + [16] * 3 + [17] * 4 + [18] * 7 + [19] * 12
        + [20] * 14 + [21] * 10 + [22] * 5,
        "waiters": [("kadikoy.ayse", "Ayşe Yılmaz"), ("kadikoy.mert", "Mert Kaya"), ("kadikoy.selin", "Selin Aydın")],
        "kitchen": [("kadikoy.usta", "Hakan Usta")],
        "catalog": [
            ("yemekler", "Yemekler", False, [
                ("ana_yemek", "Ana Yemek", [("Izgara Köfte", 240), ("Tavuk Şiş", 210), ("Adana Kebap", 280), ("Kuzu Pirzola", 420)]),
                ("baslangic", "Başlangıç", [("Mercimek Çorbası", 95), ("Humus", 110), ("Sigara Böreği", 120)]),
            ]),
            ("tatlilar", "Tatlılar", False, [
                ("tatli", "Tatlı", [("Künefe", 160), ("Sütlaç", 90), ("Baklava", 180)]),
            ]),
            ("icecekler", "İçecekler", True, [
                ("soguk_icecek", "Soğuk İçecek", [("Ayran", 40), ("Limonata", 70), ("Kola", 55), ("Su", 15)]),
                ("sicak_icecek", "Sıcak İçecek", [("Çay", 25), ("Türk Kahvesi", 75)]),
            ]),
        ],
    },
    {
        "name": "Beşiktaş Şubesi",
        "address": "Barbaros Blv. No:45, Beşiktaş",
        "menu": "Mobil Test Beşiktaş",
        "tables": 8,
        "bills_per_day": [14, 15, 15, 16, 22, 28, 20],
        "card_share": 0.58,
        "qr_share": 0.15,
        "hours": [8] * 5 + [9] * 9 + [10] * 10 + [11] * 7 + [12] * 6 + [13] * 6 + [14] * 4 + [15] * 5 + [16] * 6
        + [17] * 3 + [18] * 2,
        "waiters": [("besiktas.deniz", "Deniz Çelik"), ("besiktas.oguz", "Oğuz Er")],
        "kitchen": [("besiktas.usta", "Ali Usta")],
        "catalog": [
            ("kahvalti", "Kahvaltı", False, [
                ("kahvalti_tabagi", "Kahvaltı", [("Serpme Kahvaltı", 450), ("Menemen", 140), ("Sucuklu Yumurta", 160)]),
                ("tost", "Tost & Sandviç", [("Kaşarlı Tost", 110), ("Club Sandviç", 190)]),
            ]),
            ("tatlilar", "Tatlılar", False, [
                ("pasta", "Pasta", [("Cheesecake", 170), ("Brownie", 140)]),
            ]),
            ("kahveler", "Kahveler", True, [
                ("espresso_bazli", "Espresso Bazlı", [("Latte", 95), ("Americano", 80), ("Flat White", 100)]),
                ("demleme", "Demleme", [("Çay", 25), ("Filtre Kahve", 85)]),
            ]),
        ],
    },
]


def money(value: Decimal) -> Decimal:
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def connect():
    psycopg2.extras.register_uuid()
    conn = psycopg2.connect(
        host="185.184.210.52",
        port=5433,
        dbname="algoryqrdb-stage",
        user="postgres",
        password="postgres_stage",
    )
    conn.autocommit = False
    return conn


def public_id() -> str:
    return base64.urlsafe_b64encode(secrets.token_bytes(16)).decode().rstrip("=")


def ensure_branch(cur, spec, now) -> int:
    cur.execute(
        "SELECT id FROM tbl_branch WHERE merchant_id = %s AND name = %s AND is_deleted = FALSE",
        (MERCHANT_ID, spec["name"]),
    )
    row = cur.fetchone()
    if row:
        return row[0]
    cur.execute(
        """
        INSERT INTO tbl_branch (
            created_at, is_deleted, updated_at, active, address, grandfathered, name, merchant_id,
            kitchen_enabled, print_kitchen_enabled, courier_enabled, order_flow_id
        ) VALUES (%s, FALSE, %s, TRUE, %s, FALSE, %s, %s, TRUE, FALSE, FALSE, %s)
        RETURNING id
        """,
        (now, now, spec["address"], spec["name"], MERCHANT_ID, ORDER_FLOW_ID),
    )
    return cur.fetchone()[0]


def ensure_menu(cur, spec, branch_id, now) -> int:
    cur.execute(
        "SELECT menu_id FROM tbl_menu WHERE merchant_id = %s AND branch_id = %s AND is_deleted = FALSE",
        (MERCHANT_ID, branch_id),
    )
    row = cur.fetchone()
    if row:
        return row[0]
    details = {"themeId": "luxury", "branchId": branch_id, "businessName": spec["menu"]}
    cur.execute(
        """
        INSERT INTO tbl_qr (created_at, is_deleted, updated_at, details, qr_name, merchant_id, purchase_id, active)
        VALUES (%s, FALSE, %s, %s::jsonb, %s, %s, 0, TRUE)
        RETURNING qr_id
        """,
        (now, now, json.dumps(details, ensure_ascii=False), spec["menu"], MERCHANT_ID),
    )
    qr_id = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO tbl_menu (
            created_at, is_deleted, updated_at, active, address, business_name, qr_id, theme_id, merchant_id,
            chef_avatar_key, public_access_enabled, rating_avg, rating_count, branch_id, public_id, channel
        ) VALUES (%s, FALSE, %s, TRUE, %s, %s, %s, 'luxury', %s, 'default', TRUE, 0, 0, %s, %s, 'QR')
        RETURNING menu_id
        """,
        (now, now, spec["address"], spec["menu"], qr_id, MERCHANT_ID, branch_id, public_id()),
    )
    return cur.fetchone()[0]


def ensure_catalog(cur, spec, menu_id, now) -> list[tuple[int, str, Decimal]]:
    """Categories, sub-categories and priced products; existing rows are matched by slug or name."""
    product_sort = 0
    for cat_order, (slug, name, skip_kitchen, subs) in enumerate(spec["catalog"], start=1):
        cur.execute("SELECT id FROM tbl_menu_category WHERE menu_id = %s AND slug = %s", (menu_id, slug))
        row = cur.fetchone()
        if row:
            category_id = row[0]
        else:
            cur.execute(
                """
                INSERT INTO tbl_menu_category (
                    menu_id, slug, name, sort_order, created_at, updated_at, is_deleted, merchant_id, skip_kitchen
                ) VALUES (%s, %s, %s, %s, %s, %s, FALSE, %s, %s)
                RETURNING id
                """,
                (menu_id, slug, name, cat_order, now, now, MERCHANT_ID, skip_kitchen),
            )
            category_id = cur.fetchone()[0]
        for sub_order, (sub_slug, sub_name, products) in enumerate(subs, start=1):
            cur.execute(
                "SELECT id FROM tbl_menu_sub_category WHERE menu_id = %s AND slug = %s", (menu_id, sub_slug)
            )
            row = cur.fetchone()
            if row:
                sub_id = row[0]
            else:
                cur.execute(
                    """
                    INSERT INTO tbl_menu_sub_category (
                        menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, FALSE)
                    RETURNING id
                    """,
                    (menu_id, category_id, sub_slug, sub_name, sub_order, now, now),
                )
                sub_id = cur.fetchone()[0]
            for product_name, price in products:
                product_sort += 1
                cur.execute(
                    "SELECT product_id FROM tbl_menu_products WHERE menu_id = %s AND name = %s AND is_deleted = FALSE",
                    (menu_id, product_name),
                )
                if cur.fetchone():
                    continue
                cur.execute(
                    """
                    INSERT INTO tbl_menu_products (
                        created_at, is_deleted, updated_at, available, currency, menu_id, name, price, sort_order,
                        chef_recommended, nutrition, rating_avg, rating_count, sub_category_id, merchant_id
                    ) VALUES (%s, FALSE, %s, TRUE, 'TRY', %s, %s, %s, %s, FALSE, %s, 0, 0, %s, %s)
                    """,
                    (now, now, menu_id, product_name, Decimal(price), product_sort, Json(NUTRITION), sub_id, MERCHANT_ID),
                )
    cur.execute(
        """
        SELECT product_id, name, price FROM tbl_menu_products
        WHERE menu_id = %s AND is_deleted = FALSE AND price > 0
        ORDER BY sort_order
        """,
        (menu_id,),
    )
    return [(row[0], row[1], Decimal(row[2])) for row in cur.fetchall()]


def ensure_tables(cur, menu_id, count, now) -> list[tuple[int, str]]:
    cur.execute(
        "SELECT name, id FROM tbl_restaurant_table WHERE menu_id = %s AND deleted = FALSE", (menu_id,)
    )
    by_name = dict(cur.fetchall())
    tables = []
    for number in range(1, count + 1):
        name = f"Masa {number}"
        if name not in by_name:
            cur.execute(
                """
                INSERT INTO tbl_restaurant_table (
                    active, created_at, menu_id, name, public_token, table_number, updated_at, capacity,
                    layout_x, layout_y, layout_rotation, layout_shape, deleted, merchant_id
                ) VALUES (TRUE, %s, %s, %s, %s, %s, %s, 4, %s, %s, 0, 'rect', FALSE, %s)
                RETURNING id
                """,
                (now, menu_id, name, secrets.token_hex(16), number, now,
                 float((number - 1) % 4) * 140, float((number - 1) // 4) * 120, MERCHANT_ID),
            )
            by_name[name] = cur.fetchone()[0]
        tables.append((by_name[name], name))
    return tables


def ensure_staff(cur, spec, branch_id, now) -> list[int]:
    waiter_ids = []
    people = [(u, n, "WAITER") for u, n in spec["waiters"]] + [(u, n, "KITCHEN") for u, n in spec["kitchen"]]
    for username, display_name, role in people:
        waiter = role == "WAITER"
        cur.execute(
            "SELECT id FROM tbl_merchant_staff WHERE merchant_id = %s AND username = %s", (MERCHANT_ID, username)
        )
        row = cur.fetchone()
        if row:
            staff_id = row[0]
        else:
            cur.execute(
                """
                INSERT INTO tbl_merchant_staff (
                    active, commission_enabled, commission_type, commission_value, created_at, display_name,
                    merchant_id, password_hash, updated_at, username, branch_id, staff_role, role_id
                ) VALUES (TRUE, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                RETURNING id
                """,
                (waiter, "PERCENT" if waiter else None, Decimal("5.00") if waiter else None, now, display_name,
                 MERCHANT_ID, PASSWORD_HASH, now, username, branch_id, role,
                 WAITER_ROLE_ID if waiter else KITCHEN_ROLE_ID),
            )
            staff_id = cur.fetchone()[0]
        if waiter:
            waiter_ids.append(staff_id)
    return waiter_ids


def cleanup(cur, menu_id):
    cur.execute("SELECT id, table_session_id FROM tbl_menu_order WHERE menu_id = %s AND note = %s", (menu_id, SEED))
    orders = cur.fetchall()
    if not orders:
        return
    order_ids = [row[0] for row in orders]
    session_ids = [row[1] for row in orders]
    cur.execute("SELECT id FROM tbl_table_bill WHERE table_session_id = ANY(%s::uuid[])", (session_ids,))
    bill_ids = [row[0] for row in cur.fetchall()]
    cur.execute(
        "DELETE FROM tbl_merchant_staff_commission WHERE bill_id = ANY(%s) OR order_id = ANY(%s)", (bill_ids, order_ids)
    )
    cur.execute("DELETE FROM tbl_bill_payment WHERE bill_id = ANY(%s)", (bill_ids,))
    cur.execute("DELETE FROM tbl_table_bill_item WHERE bill_id = ANY(%s)", (bill_ids,))
    cur.execute("DELETE FROM tbl_user_accounting_entry WHERE source_bill_id = ANY(%s)", (bill_ids,))
    cur.execute("UPDATE tbl_menu_order SET bill_id = NULL WHERE id = ANY(%s)", (order_ids,))
    cur.execute("DELETE FROM tbl_table_bill WHERE id = ANY(%s)", (bill_ids,))
    cur.execute("DELETE FROM tbl_menu_order_item WHERE order_id = ANY(%s)", (order_ids,))
    cur.execute("DELETE FROM tbl_menu_order WHERE id = ANY(%s)", (order_ids,))
    cur.execute("DELETE FROM tbl_table_session WHERE id = ANY(%s::uuid[])", (session_ids,))


def insert_returning(cur, sql, rows):
    ids = []
    for offset in range(0, len(rows), 500):
        chunk = rows[offset : offset + 500]
        ids.extend(row[0] for row in execute_values(cur, sql, chunk, fetch=True, page_size=len(chunk)))
    if len(ids) != len(rows):
        raise RuntimeError(f"returning count {len(ids)} != {len(rows)}")
    return ids


def execute_chunks(cur, sql, rows):
    for offset in range(0, len(rows), 800):
        execute_values(cur, sql, rows[offset : offset + 800], page_size=800)


def seed_sales(cur, rng, spec, branch_id, menu_id, products, tables, waiter_ids, now):
    """Closed, paid bills from DAYS ago until now; today stops at the current hour so "1G" looks like a live day."""
    today = now.date()
    start = today - timedelta(days=DAYS - 1)
    # A slow ramp-up makes the comparison with the previous period show a real change.
    plans = []
    day = start
    while day <= today:
        growth = 0.75 + 0.35 * (day - start).days / DAYS
        count = max(1, int(spec["bills_per_day"][day.weekday()] * growth * rng.uniform(0.85, 1.15)))
        on_duty = rng.sample(waiter_ids, min(2, len(waiter_ids)))
        for _ in range(count):
            hour = rng.choice(spec["hours"])
            opened_at = datetime(day.year, day.month, day.day, hour, rng.randrange(60), rng.randrange(60))
            served_at = opened_at + timedelta(minutes=rng.randint(20, 80))
            if served_at >= now:
                continue
            if served_at.date() != day:
                served_at = datetime(day.year, day.month, day.day, 23, 50)
            picked = rng.sample(products, rng.randint(2, min(4, len(products))))
            lines = []
            total = Decimal("0.00")
            for product_id, name, price in picked:
                qty = rng.choice([1, 1, 1, 2, 2, 3])
                line_total = money(price * qty)
                total += line_total
                lines.append((product_id, name, price, qty, line_total))
            tip = money(total * Decimal(str(rng.uniform(0.05, 0.12)))) if rng.random() < 0.3 else Decimal("0.00")
            table_id, table_name = rng.choice(tables)
            plans.append({
                "opened_at": opened_at,
                "served_at": served_at,
                "waiter_id": rng.choice(on_duty),
                "table_id": table_id,
                "table_name": table_name,
                "session_id": uuid.uuid4(),
                "lines": lines,
                "total": total,
                "tip": tip,
                "method": "CARD" if rng.random() < spec["card_share"] else "CASH",
                "source": "QR" if rng.random() < spec["qr_share"] else "WAITER",
                "commission": money(total * Decimal("0.05")),
            })
        day += timedelta(days=1)

    insert_returning(
        cur,
        "INSERT INTO tbl_table_session (id, created_at, expires_at, menu_id, revoked, session_token, table_id) "
        "VALUES %s RETURNING id",
        [(p["session_id"], p["opened_at"], p["served_at"] + timedelta(hours=4), menu_id, True, secrets.token_hex(16),
          p["table_id"]) for p in plans],
    )
    order_ids = insert_returning(
        cur,
        """
        INSERT INTO tbl_menu_order (
            commission_amount, confirmed_at, created_at, currency, menu_id, note, status, submitted_at, table_id,
            table_session_id, total_amount, updated_at, staff_id, created_by_staff_id, order_source, prepared_at,
            ready_at, served_at
        ) VALUES %s RETURNING id
        """,
        [(p["commission"], p["opened_at"] + timedelta(minutes=2), p["opened_at"], "TRY", menu_id, SEED, "SERVED",
          p["opened_at"], p["table_id"], p["session_id"], p["total"], p["served_at"], p["waiter_id"],
          None if p["source"] == "QR" else p["waiter_id"], p["source"], p["opened_at"] + timedelta(minutes=12),
          p["opened_at"] + timedelta(minutes=16), p["served_at"]) for p in plans],
    )
    bill_ids = insert_returning(
        cur,
        """
        INSERT INTO tbl_table_bill (
            closed_at, closed_by_staff_id, created_at, currency, menu_id, opened_at, opened_by_staff_id,
            payment_method, status, table_id, table_session_id, tip_amount, total_amount, updated_at, cover_count
        ) VALUES %s RETURNING id
        """,
        [(p["served_at"], p["waiter_id"], p["opened_at"], "TRY", menu_id, p["opened_at"], p["waiter_id"], p["method"],
          "CLOSED", p["table_id"], p["session_id"], p["tip"] or None, p["total"], p["served_at"], rng.randint(1, 5))
         for p in plans],
    )
    cur.execute(
        "UPDATE tbl_menu_order o SET bill_id = b.id FROM tbl_table_bill b "
        "WHERE o.id = ANY(%s) AND b.id = ANY(%s) AND o.table_session_id = b.table_session_id",
        (order_ids, bill_ids),
    )

    order_items, bill_items = [], []
    for p, order_id, bill_id in zip(plans, order_ids, bill_ids):
        for product_id, name, price, qty, line_total in p["lines"]:
            order_items.append((line_total, product_id, name, qty, price, order_id, Json([])))
            bill_items.append((p["waiter_id"], p["opened_at"], line_total, product_id, name, qty, order_id, price,
                               p["served_at"], bill_id, qty))
    execute_chunks(
        cur,
        "INSERT INTO tbl_menu_order_item (line_total, product_id, product_name, quantity, unit_price, order_id, "
        "selected_options) VALUES %s",
        order_items,
    )
    bill_item_ids = insert_returning(
        cur,
        """
        INSERT INTO tbl_table_bill_item (
            added_by_staff_id, created_at, line_total, product_id, product_name, quantity, source_order_id,
            unit_price, updated_at, bill_id, paid_quantity
        ) VALUES %s RETURNING id
        """,
        bill_items,
    )

    payments, commissions, sales = [], [], []
    cursor = 0
    for p, order_id, bill_id in zip(plans, order_ids, bill_ids):
        for _product_id, _name, _price, qty, line_total in p["lines"]:
            payments.append((line_total, p["served_at"], p["served_at"], p["method"], qty, False, p["waiter_id"],
                             bill_id, bill_item_ids[cursor]))
            cursor += 1
        if p["tip"] > 0:
            payments.append((p["tip"], p["served_at"], p["served_at"], p["method"], 0, True, p["waiter_id"], bill_id,
                             None))
        commissions.append((p["commission"], p["total"], bill_id, Decimal("5.00"), p["served_at"], "TRY", menu_id,
                            order_id, "PERCENT_ORDER", p["waiter_id"], branch_id))
        sales.append((p["total"], p["served_at"], p["waiter_id"], "TRY", "GELIR", menu_id, SEED, p["served_at"], bill_id,
                      order_id, "BILL_SALE", f"Adisyon - {p['table_name']}", p["served_at"], MERCHANT_ID, p["total"],
                      p["tip"] or None))
    execute_chunks(
        cur,
        "INSERT INTO tbl_bill_payment (amount, created_at, paid_at, payment_method, quantity_paid, is_tip, staff_id, "
        "bill_id, bill_item_id) VALUES %s",
        payments,
    )
    execute_chunks(
        cur,
        "INSERT INTO tbl_merchant_staff_commission (amount, base_amount, bill_id, commission_value_snapshot, "
        "created_at, currency, menu_id, order_id, record_type, staff_id, branch_id) VALUES %s",
        commissions,
    )
    execute_chunks(
        cur,
        """
        INSERT INTO tbl_user_accounting_entry (
            amount, created_at, created_by_staff_id, currency, entry_type, menu_id, note, occurred_at, source_bill_id,
            source_order_id, source_type, title, updated_at, merchant_id, order_amount, tip_amount
        ) VALUES %s
        """,
        sales,
    )
    return len(plans), sum((p["total"] for p in plans), Decimal("0"))


def main():
    rng = Random(20261102)
    now = datetime.now().replace(microsecond=0)
    conn = connect()
    cur = conn.cursor()
    cur.execute("SELECT id FROM tbl_merchant WHERE id = %s", (MERCHANT_ID,))
    if not cur.fetchone():
        raise SystemExit(f"merchant {MERCHANT_ID} not found")
    for spec in BRANCHES:
        branch_id = ensure_branch(cur, spec, now)
        menu_id = ensure_menu(cur, spec, branch_id, now)
        products = ensure_catalog(cur, spec, menu_id, now)
        tables = ensure_tables(cur, menu_id, spec["tables"], now)
        waiter_ids = ensure_staff(cur, spec, branch_id, now)
        cleanup(cur, menu_id)
        bills, revenue = seed_sales(cur, rng, spec, branch_id, menu_id, products, tables, waiter_ids, now)
        print(f"{spec['name']}: branch {branch_id}, menu {menu_id}, {len(products)} products, {len(tables)} tables, "
              f"{len(waiter_ids)} waiters, {bills} bills, ₺{revenue}")
    conn.commit()
    print("committed")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
