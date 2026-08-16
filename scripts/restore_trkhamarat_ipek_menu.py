#!/usr/bin/env python3
"""Restore soft-deleted İPEK KAFE menu (qr_id=10, menu_id=8) for trkhamarat@gmail.com."""

from __future__ import annotations

import os

import psycopg2

USER_ID = 1
USER_EMAIL = "trkhamarat@gmail.com"
QR_ID = 10
MENU_ID = 8


def connect():
    return psycopg2.connect(
        host=os.environ.get("DB_HOST", "185.184.210.52"),
        port=os.environ.get("DB_PORT", "5432"),
        dbname=os.environ.get("DB_NAME", "algoryqrdb"),
        user=os.environ.get("DB_USERNAME", "postgres"),
        password=os.environ.get(
            "DB_PASSWORD",
            "AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
        ),
    )


def main() -> None:
    with connect() as conn:
        conn.autocommit = False
        with conn.cursor() as cur:
            cur.execute("SELECT id, email FROM tbl_user WHERE id = %s", (USER_ID,))
            user = cur.fetchone()
            if not user or user[1] != USER_EMAIL:
                raise RuntimeError(f"Expected user {USER_EMAIL} (id={USER_ID}), found {user}")

            cur.execute(
                """
                SELECT id FROM tbl_purchase
                WHERE user_id = %s AND status = 'ACTIVE'
                ORDER BY id DESC
                LIMIT 1
                """,
                (USER_ID,),
            )
            purchase_row = cur.fetchone()
            if not purchase_row:
                raise RuntimeError("No active purchase found for user")
            purchase_id = purchase_row[0]

            cur.execute(
                """
                UPDATE tbl_qr
                SET is_deleted = FALSE,
                    active = TRUE,
                    purchase_id = %s,
                    updated_at = NOW()
                WHERE qr_id = %s AND user_id = %s
                """,
                (purchase_id, QR_ID, USER_ID),
            )
            if cur.rowcount != 1:
                raise RuntimeError(f"QR restore failed for qr_id={QR_ID}")

            cur.execute(
                """
                UPDATE tbl_menu
                SET is_deleted = FALSE,
                    active = TRUE,
                    updated_at = NOW()
                WHERE menu_id = %s AND user_id = %s AND qr_id = %s
                """,
                (MENU_ID, USER_ID, QR_ID),
            )
            if cur.rowcount != 1:
                raise RuntimeError(f"Menu restore failed for menu_id={MENU_ID}")

            cur.execute(
                """
                SELECT count(*) FROM tbl_qr
                WHERE user_id = %s AND is_deleted = FALSE
                """,
                (USER_ID,),
            )
            active_qr_count = cur.fetchone()[0]

            cur.execute(
                """
                UPDATE tbl_user_entitlement
                SET used_quantity = LEAST(%s, total_quantity),
                    remaining_quantity = GREATEST(total_quantity - LEAST(%s, total_quantity), 0),
                    updated_at = NOW()
                WHERE user_id = %s
                  AND purchase_id = %s
                  AND product_code = 'QR_CREATE'
                  AND unlimited = FALSE
                """,
                (active_qr_count, active_qr_count, USER_ID, purchase_id),
            )

            cur.execute(
                """
                UPDATE tbl_user_entitlement
                SET used_quantity = 1,
                    remaining_quantity = GREATEST(total_quantity - 1, 0),
                    updated_at = NOW()
                WHERE user_id = %s
                  AND purchase_id = %s
                  AND product_code = 'QR_MENU'
                  AND unlimited = FALSE
                """,
                (USER_ID, purchase_id),
            )

        conn.commit()

    print(f"Restored İPEK KAFE for {USER_EMAIL}")
    print(f"- qr_id={QR_ID}, menu_id={MENU_ID}, purchase_id={purchase_id}")


if __name__ == "__main__":
    main()
