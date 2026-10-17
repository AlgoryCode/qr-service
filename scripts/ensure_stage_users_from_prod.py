#!/usr/bin/env python3
"""Ensure ilhanA and Tavşan stage tbl_user rows exist (cloned from prod auth fields)."""

from __future__ import annotations

import psycopg2
from psycopg2.extras import RealDictCursor

PROD = dict(
    host="185.184.210.52",
    port=5432,
    dbname="algoryqrdb",
    user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable",
    connect_timeout=15,
)

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=15,
)

PROD_USER_IDS = (25, 13)
EMAILS = ("a11_fen@hotmail.com", "tavsancoffe@gmail.com")

CLONE_COLUMNS = [
    "email",
    "first_name",
    "last_name",
    "password",
    "phone",
    "provider",
    "provider_subject",
    "role",
    "email_verified",
    "business_type",
    "usage_purpose",
]


def main() -> None:
    prod = psycopg2.connect(**PROD)
    stage = psycopg2.connect(**STAGE)
    stage.autocommit = False
    try:
        with prod.cursor(cursor_factory=RealDictCursor) as pc, stage.cursor() as sc:
            for prod_id, email in zip(PROD_USER_IDS, EMAILS, strict=True):
                sc.execute(
                    "SELECT id FROM tbl_user WHERE lower(email)=lower(%s) AND deleted_at IS NULL",
                    (email,),
                )
                if sc.fetchone():
                    print("exists", email)
                    continue

                pc.execute(
                    f"""
                    SELECT {", ".join(CLONE_COLUMNS)}
                    FROM tbl_user WHERE id = %s
                    """,
                    (prod_id,),
                )
                row = pc.fetchone()
                if not row:
                    raise RuntimeError(f"prod user {prod_id} missing")

                cols = list(row.keys())
                vals = [row[c] for c in cols]
                placeholders = ", ".join(["%s"] * len(cols))
                sc.execute(
                    f"""
                    INSERT INTO tbl_user ({", ".join(cols)}, created_at, updated_at)
                    VALUES ({placeholders}, NOW(), NOW())
                    RETURNING id
                    """,
                    vals,
                )
                new_id = sc.fetchone()[0]
                print("created", email, "stage_user_id=", new_id)

        stage.commit()
    except Exception:
        stage.rollback()
        raise
    finally:
        prod.close()
        stage.close()


if __name__ == "__main__":
    main()
