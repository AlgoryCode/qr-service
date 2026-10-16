#!/usr/bin/env python3
"""One year of demo sales for trkhamarat@gmail.com on the stage database.

Target: İPEK KAFE (menu 8, Sefaköy). Rows are marked note = SEED_YEAR and replaced on rerun.
"""

from __future__ import annotations

import secrets
import uuid
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP
from random import Random

import psycopg2
from psycopg2.extras import Json, execute_values

SEED = "SEED_YEAR"
MENU_ID = 8
BRANCH_ID = 5
START = date(2025, 10, 6)
END = date(2026, 10, 5)
PASSWORD_HASH = "$2b$08$u1uoB/4zar3qJgmKUtsS0OeOOzWXaFtrj/SX12Lm6dl2KLencDqoK"

WAITERS = [
    ("ipek.ayse", "Ayşe Yılmaz"),
    ("ipek.mehmet", "Mehmet Kaya"),
    ("ipek.elif", "Elif Demir"),
    ("ipek.can", "Can Öztürk"),
]
KITCHEN = [
    ("ipek.zeynep", "Zeynep Arslan"),
    ("ipek.burak", "Burak Şahin"),
]
EXPENSES = [
    ("Kira", Decimal("1600.00")),
    ("Enerji ve su", Decimal("280.00")),
    ("Personel sabit gider", Decimal("1900.00")),
]
MONTHLY_COSTS = [
    ("Kira", Decimal("95000.00")),
    ("Personel maaş", Decimal("160000.00")),
    ("Enerji", Decimal("22000.00")),
]
HOURS = (
    [11] * 3
    + [12] * 8
    + [13] * 14
    + [14] * 10
    + [15] * 4
    + [16] * 2
    + [17] * 3
    + [18] * 6
    + [19] * 12
    + [20] * 16
    + [21] * 14
    + [22] * 8
    + [23] * 3
)


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


def bills_for_day(day: date) -> int:
    base = [16, 20, 20, 21, 30, 36, 26][day.weekday()]
    if day.month in (6, 7, 8):
        return int(base * 1.15)
    if day.month in (1, 2):
        return int(base * 0.9)
    return base


def insert_returning(cur, sql: str, rows: list[tuple]):
    ids = []
    for offset in range(0, len(rows), 500):
        chunk = rows[offset : offset + 500]
        fetched = execute_values(cur, sql, chunk, fetch=True, page_size=len(chunk))
        ids.extend(row[0] for row in fetched)
    if len(ids) != len(rows):
        raise RuntimeError(f"returning count {len(ids)} != {len(rows)}")
    return ids


def execute_chunks(cur, sql: str, rows: list[tuple]):
    for offset in range(0, len(rows), 800):
        execute_values(cur, sql, rows[offset : offset + 800], page_size=800)


def cleanup(cur, user_id: int):
    cur.execute(
        "SELECT id, table_session_id FROM tbl_menu_order WHERE menu_id = %s AND note = %s",
        (MENU_ID, SEED),
    )
    orders = cur.fetchall()
    order_ids = [row[0] for row in orders]
    session_ids = [row[1] for row in orders]
    if order_ids:
        cur.execute(
            "SELECT id FROM tbl_table_bill WHERE table_session_id = ANY(%s::uuid[])",
            (session_ids,),
        )
        bill_ids = [row[0] for row in cur.fetchall()]
        if bill_ids:
            cur.execute(
                "DELETE FROM tbl_merchant_staff_commission WHERE bill_id = ANY(%s) OR order_id = ANY(%s)",
                (bill_ids, order_ids),
            )
            cur.execute("DELETE FROM tbl_bill_payment WHERE bill_id = ANY(%s)", (bill_ids,))
            cur.execute("DELETE FROM tbl_table_bill_item WHERE bill_id = ANY(%s)", (bill_ids,))
            cur.execute(
                "DELETE FROM tbl_user_accounting_entry WHERE source_bill_id = ANY(%s)",
                (bill_ids,),
            )
            cur.execute("DELETE FROM tbl_table_bill WHERE id = ANY(%s)", (bill_ids,))
        else:
            cur.execute(
                "DELETE FROM tbl_merchant_staff_commission WHERE order_id = ANY(%s)",
                (order_ids,),
            )
        cur.execute("DELETE FROM tbl_menu_order_item WHERE order_id = ANY(%s)", (order_ids,))
        cur.execute("DELETE FROM tbl_menu_order WHERE id = ANY(%s)", (order_ids,))
        cur.execute("DELETE FROM tbl_table_session WHERE id = ANY(%s::uuid[])", (session_ids,))
    cur.execute(
        """
        DELETE FROM tbl_work_shift_staff
        WHERE shift_id IN (SELECT id FROM tbl_work_shift WHERE menu_id = %s AND note = %s)
        """,
        (MENU_ID, SEED),
    )
    cur.execute("DELETE FROM tbl_work_shift WHERE menu_id = %s AND note = %s", (MENU_ID, SEED))
    cur.execute(
        """
        DELETE FROM tbl_user_accounting_entry
        WHERE user_id = %s AND menu_id = %s AND note = %s AND source_type = 'MANUAL'
        """,
        (user_id, MENU_ID, SEED),
    )


def ensure_staff(cur, user_id: int) -> list[int]:
    waiter_ids = []
    now = datetime.now()
    kitchen_names = {name for _, name in KITCHEN}
    for username, display_name in WAITERS + KITCHEN:
        role = "KITCHEN" if display_name in kitchen_names else "WAITER"
        cur.execute(
            "SELECT id FROM tbl_merchant_staff WHERE merchant_id = %s AND username = %s",
            (user_id, username),
        )
        found = cur.fetchone()
        commission_enabled = role == "WAITER"
        commission_type = "PERCENT" if commission_enabled else None
        commission_value = Decimal("5.00") if commission_enabled else None
        if found:
            staff_id = found[0]
            cur.execute(
                """
                UPDATE tbl_merchant_staff
                SET branch_id = %s, display_name = %s, staff_role = %s, active = TRUE,
                    commission_enabled = %s, commission_type = %s, commission_value = %s,
                    updated_at = %s
                WHERE id = %s
                """,
                (
                    BRANCH_ID,
                    display_name,
                    role,
                    commission_enabled,
                    commission_type,
                    commission_value,
                    now,
                    staff_id,
                ),
            )
        else:
            cur.execute(
                """
                INSERT INTO tbl_merchant_staff (
                    merchant_id, branch_id, username, password_hash, display_name, active,
                    staff_role, commission_enabled, commission_type, commission_value,
                    created_at, updated_at
                ) VALUES (%s, %s, %s, %s, %s, TRUE, %s, %s, %s, %s, %s, %s)
                RETURNING id
                """,
                (
                    user_id,
                    BRANCH_ID,
                    username,
                    PASSWORD_HASH,
                    display_name,
                    role,
                    commission_enabled,
                    commission_type,
                    commission_value,
                    now,
                    now,
                ),
            )
            staff_id = cur.fetchone()[0]
        if role == "WAITER":
            waiter_ids.append(staff_id)
    return waiter_ids


def ensure_tables(cur) -> list[int]:
    cur.execute(
        """
        SELECT id, name FROM tbl_restaurant_table
        WHERE menu_id = %s AND deleted = FALSE AND name LIKE 'Masa %%'
        """,
        (MENU_ID,),
    )
    by_name = {row[1]: row[0] for row in cur.fetchall()}
    now = datetime.now()
    ids = []
    for number in range(1, 13):
        name = f"Masa {number}"
        if name in by_name:
            ids.append(by_name[name])
            continue
        cur.execute(
            """
            INSERT INTO tbl_restaurant_table (
                active, created_at, menu_id, name, public_token, table_number, updated_at,
                capacity, layout_x, layout_y, layout_rotation, layout_shape, deleted
            ) VALUES (
                TRUE, %s, %s, %s, %s, %s, %s, 4, %s, %s, 0, 'rect', FALSE
            )
            RETURNING id
            """,
            (
                now,
                MENU_ID,
                name,
                secrets.token_hex(16),
                number,
                now,
                float((number - 1) % 4) * 140,
                float((number - 1) // 4) * 120,
            ),
        )
        ids.append(cur.fetchone()[0])
    return ids


def ensure_expenses(cur):
    cur.execute(
        "SELECT COUNT(*) FROM tbl_menu_fixed_expense WHERE menu_id = %s AND active = TRUE",
        (MENU_ID,),
    )
    if cur.fetchone()[0]:
        return
    now = datetime.now()
    execute_values(
        cur,
        """
        INSERT INTO tbl_menu_fixed_expense
            (menu_id, title, daily_amount, active, created_at, updated_at)
        VALUES %s
        """,
        [(MENU_ID, title, amount, True, now, now) for title, amount in EXPENSES],
    )


def load_products(cur):
    cur.execute(
        """
        SELECT product_id, name, price
        FROM tbl_menu_products
        WHERE menu_id = %s AND is_deleted = FALSE AND price IS NOT NULL AND price > 0
        """,
        (MENU_ID,),
    )
    products = [(row[0], (row[1] or "Ürün")[:240], Decimal(row[2])) for row in cur.fetchall()]
    if len(products) < 5:
        raise RuntimeError("menu has too few priced products")
    return products


def main():
    rng = Random(20261006)
    conn = connect()
    cur = conn.cursor()
    cur.execute("SELECT id FROM tbl_user WHERE lower(email) = %s", ("trkhamarat@gmail.com",))
    user = cur.fetchone()
    if not user:
        raise SystemExit("stage user not found")
    user_id = user[0]
    cur.execute(
        "SELECT menu_id, business_name, branch_id FROM tbl_menu WHERE menu_id = %s AND user_id = %s",
        (MENU_ID, user_id),
    )
    menu = cur.fetchone()
    if not menu:
        raise SystemExit("İPEK KAFE menu not found for this user")

    print("cleanup previous seed")
    cleanup(cur, user_id)
    waiter_ids = ensure_staff(cur, user_id)
    kitchen_ids = []
    cur.execute(
        """
        SELECT id FROM tbl_merchant_staff
        WHERE merchant_id = %s AND username IN ('ipek.zeynep', 'ipek.burak')
        ORDER BY username
        """,
        (user_id,),
    )
    kitchen_ids = [row[0] for row in cur.fetchall()]
    table_ids = ensure_tables(cur)
    ensure_expenses(cur)
    products = load_products(cur)
    table_names = {}
    cur.execute("SELECT id, name FROM tbl_restaurant_table WHERE id = ANY(%s)", (table_ids,))
    for row in cur.fetchall():
        table_names[row[0]] = row[1]

    print(f"building {START} .. {END} for menu {menu[1]}")
    session_rows = []
    order_rows = []
    bill_rows = []
    item_plans = []
    shift_rows = []
    manual_rows = []
    day = START
    day_index = 0
    while day <= END:
        count = bills_for_day(day)
        on_duty = [
            waiter_ids[day_index % len(waiter_ids)],
            waiter_ids[(day_index + 1) % len(waiter_ids)],
        ]
        shift_open = datetime(day.year, day.month, day.day, 10, 0, 0)
        shift_close = datetime(day.year, day.month, day.day, 23, 30, 0)
        shift_rows.append((on_duty[0], shift_open, shift_close, on_duty))
        for _ in range(count):
            hour = rng.choice(HOURS)
            opened_at = datetime(day.year, day.month, day.day, hour, rng.randrange(0, 60), rng.randrange(0, 50))
            served_at = opened_at + timedelta(minutes=rng.randint(18, 75))
            if served_at.date() != day:
                served_at = datetime(day.year, day.month, day.day, 23, 45, 0)
            confirmed_at = opened_at + timedelta(minutes=2)
            prepared_at = opened_at + timedelta(minutes=12)
            ready_at = opened_at + timedelta(minutes=16)
            waiter_id = on_duty[rng.randrange(0, 2)]
            table_id = table_ids[rng.randrange(0, len(table_ids))]
            session_id = uuid.uuid4()
            token = secrets.token_hex(16)
            picked = rng.sample(products, rng.randint(2, 4))
            lines = []
            total = Decimal("0.00")
            for product_id, name, price in picked:
                qty = rng.randint(1, 3)
                line_total = money(price * qty)
                total += line_total
                lines.append((product_id, name, price, qty, line_total))
            tip = Decimal("0.00")
            if rng.random() < 0.28:
                tip = money(total * Decimal(str(rng.uniform(0.05, 0.12))))
            method = "CARD" if rng.random() < 0.68 else "CASH"
            source = "WAITER" if rng.random() < 0.82 else "QR"
            commission = money(total * Decimal("0.05"))
            session_rows.append(
                (session_id, opened_at, served_at + timedelta(hours=4), MENU_ID, False, token, table_id)
            )
            order_rows.append(
                (
                    None,
                    commission,
                    confirmed_at,
                    opened_at,
                    "TRY",
                    MENU_ID,
                    SEED,
                    "SERVED",
                    opened_at,
                    table_id,
                    session_id,
                    total,
                    served_at,
                    waiter_id,
                    waiter_id,
                    source,
                    prepared_at,
                    ready_at,
                    served_at,
                )
            )
            bill_rows.append(
                (
                    served_at,
                    waiter_id,
                    opened_at,
                    "TRY",
                    MENU_ID,
                    opened_at,
                    waiter_id,
                    method,
                    "CLOSED",
                    table_id,
                    session_id,
                    tip if tip else None,
                    total,
                    served_at,
                    rng.randint(1, 5),
                )
            )
            item_plans.append(
                {
                    "lines": lines,
                    "waiter_id": waiter_id,
                    "opened_at": opened_at,
                    "served_at": served_at,
                    "method": method,
                    "tip": tip,
                    "total": total,
                    "commission": commission,
                    "table_name": table_names[table_id],
                }
            )
        if day.day == 1:
            for title, amount in MONTHLY_COSTS:
                occurred = datetime(day.year, day.month, day.day, 9, 0, 0)
                manual_rows.append(
                    (
                        amount,
                        occurred,
                        None,
                        "TRY",
                        "GIDER",
                        MENU_ID,
                        SEED,
                        occurred,
                        "MANUAL",
                        title,
                        occurred,
                        user_id,
                        amount,
                    )
                )
        day_index += 1
        day += timedelta(days=1)

    print(f"inserting {len(order_rows)} checks")
    session_ids = insert_returning(
        cur,
        """
        INSERT INTO tbl_table_session (
            id, created_at, expires_at, menu_id, revoked, session_token, table_id
        ) VALUES %s
        RETURNING id
        """,
        session_rows,
    )
    order_ids = insert_returning(
        cur,
        """
        INSERT INTO tbl_menu_order (
            bill_id, commission_amount, confirmed_at, created_at, currency, menu_id, note,
            status, submitted_at, table_id, table_session_id, total_amount, updated_at,
            staff_id, created_by_staff_id, order_source, prepared_at, ready_at, served_at
        ) VALUES %s
        RETURNING id
        """,
        order_rows,
    )
    bill_ids = insert_returning(
        cur,
        """
        INSERT INTO tbl_table_bill (
            closed_at, closed_by_staff_id, created_at, currency, menu_id, opened_at,
            opened_by_staff_id, payment_method, status, table_id, table_session_id,
            tip_amount, total_amount, updated_at, cover_count
        ) VALUES %s
        RETURNING id
        """,
        bill_rows,
    )

    order_items = []
    bill_item_rows = []
    bill_item_meta = []
    for index, plan in enumerate(item_plans):
        order_id = order_ids[index]
        bill_id = bill_ids[index]
        for product_id, name, price, qty, line_total in plan["lines"]:
            order_items.append(
                (line_total, product_id, name, qty, price, order_id, Json([]))
            )
            bill_item_rows.append(
                (
                    plan["waiter_id"],
                    plan["opened_at"],
                    line_total,
                    product_id,
                    name,
                    qty,
                    order_id,
                    price,
                    plan["served_at"],
                    bill_id,
                    qty,
                )
            )
            bill_item_meta.append((index, qty, line_total))

    execute_chunks(
        cur,
        """
        INSERT INTO tbl_menu_order_item (
            line_total, product_id, product_name, quantity, unit_price, order_id, selected_options
        ) VALUES %s
        """,
        order_items,
    )
    bill_item_ids = insert_returning(
        cur,
        """
        INSERT INTO tbl_table_bill_item (
            added_by_staff_id, created_at, line_total, product_id, product_name, quantity,
            source_order_id, unit_price, updated_at, bill_id, paid_quantity
        ) VALUES %s
        RETURNING id
        """,
        bill_item_rows,
    )

    payments = []
    commissions = []
    sales = []
    line_cursor = 0
    for index, plan in enumerate(item_plans):
        bill_id = bill_ids[index]
        order_id = order_ids[index]
        for _product_id, _name, _price, qty, line_total in plan["lines"]:
            item_id = bill_item_ids[line_cursor]
            line_cursor += 1
            payments.append(
                (
                    line_total,
                    plan["served_at"],
                    plan["served_at"],
                    plan["method"],
                    qty,
                    False,
                    plan["waiter_id"],
                    bill_id,
                    item_id,
                )
            )
        if plan["tip"] > 0:
            payments.append(
                (
                    plan["tip"],
                    plan["served_at"],
                    plan["served_at"],
                    plan["method"],
                    0,
                    True,
                    plan["waiter_id"],
                    bill_id,
                    None,
                )
            )
        commissions.append(
            (
                plan["commission"],
                plan["total"],
                bill_id,
                Decimal("5.00"),
                plan["served_at"],
                "TRY",
                MENU_ID,
                order_id,
                "PERCENT_ORDER",
                plan["waiter_id"],
                BRANCH_ID,
            )
        )
        sales.append(
            (
                plan["total"],
                plan["served_at"],
                plan["waiter_id"],
                "TRY",
                "GELIR",
                MENU_ID,
                SEED,
                plan["served_at"],
                bill_id,
                order_id,
                "BILL_SALE",
                f"Adisyon - {plan['table_name']}",
                plan["served_at"],
                user_id,
                plan["total"],
                plan["tip"] if plan["tip"] > 0 else None,
            )
        )

    print(f"inserting {len(payments)} payments")
    execute_chunks(
        cur,
        """
        INSERT INTO tbl_bill_payment (
            amount, created_at, paid_at, payment_method, quantity_paid, is_tip,
            staff_id, bill_id, bill_item_id
        ) VALUES %s
        """,
        payments,
    )
    execute_chunks(
        cur,
        """
        INSERT INTO tbl_merchant_staff_commission (
            amount, base_amount, bill_id, commission_value_snapshot, created_at, currency,
            menu_id, order_id, record_type, staff_id, branch_id
        ) VALUES %s
        """,
        commissions,
    )
    execute_chunks(
        cur,
        """
        INSERT INTO tbl_user_accounting_entry (
            amount, created_at, created_by_staff_id, currency, entry_type, menu_id, note,
            occurred_at, source_bill_id, source_order_id, source_type, title, updated_at,
            user_id, order_amount, tip_amount
        ) VALUES %s
        """,
        sales,
    )
    if manual_rows:
        execute_values(
            cur,
            """
            INSERT INTO tbl_user_accounting_entry (
                amount, created_at, created_by_staff_id, currency, entry_type, menu_id, note,
                occurred_at, source_type, title, updated_at, user_id, order_amount
            ) VALUES %s
            """,
            manual_rows,
        )

    cur.execute(
        """
        UPDATE tbl_menu_order o
        SET bill_id = b.id
        FROM tbl_table_bill b
        WHERE o.id = ANY(%s) AND b.id = ANY(%s) AND o.table_session_id = b.table_session_id
        """,
        (order_ids, bill_ids),
    )

    shift_ids = insert_returning(
        cur,
        """
        INSERT INTO tbl_work_shift (
            branch_id, menu_id, opened_by_staff_id, closed_by_staff_id, opened_at, closed_at,
            opening_float, closing_cash, note, status, created_at, updated_at
        ) VALUES %s
        RETURNING id
        """,
        [
            (
                BRANCH_ID,
                MENU_ID,
                opened_by,
                opened_by,
                opened_at,
                closed_at,
                Decimal("1500.00"),
                Decimal("1500.00"),
                SEED,
                "CLOSED",
                opened_at,
                closed_at,
            )
            for opened_by, opened_at, closed_at, _duty in shift_rows
        ],
    )
    shift_staff_rows = []
    for shift_id, (_opened_by, _opened_at, _closed_at, duty) in zip(shift_ids, shift_rows):
        for staff_id in duty + kitchen_ids:
            shift_staff_rows.append((shift_id, staff_id))
    execute_chunks(
        cur,
        "INSERT INTO tbl_work_shift_staff (shift_id, staff_id) VALUES %s",
        shift_staff_rows,
    )

    cur.execute(
        """
        SELECT
            COUNT(*) AS bills,
            COALESCE(SUM(total_amount), 0) AS food,
            COALESCE(SUM(tip_amount), 0) AS tips,
            MIN(closed_at)::date AS first_day,
            MAX(closed_at)::date AS last_day
        FROM tbl_table_bill
        WHERE id = ANY(%s)
        """,
        (bill_ids,),
    )
    summary = cur.fetchone()
    conn.commit()
    print(
        "committed",
        {
            "user_id": user_id,
            "menu_id": MENU_ID,
            "menu": menu[1],
            "bills": summary[0],
            "food": str(summary[1]),
            "tips": str(summary[2]),
            "first_day": str(summary[3]),
            "last_day": str(summary[4]),
            "waiters": waiter_ids,
            "tables": len(table_ids),
            "products": len(products),
        },
    )
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
