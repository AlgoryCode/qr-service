from __future__ import annotations

from datetime import datetime

import psycopg2

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

SOURCE_MENU_ID = 16
TARGET_EMAIL = "ulasbayram61@gmail.com"

PARADISE_TO_AYA_SLUG = {
    "corbalar": "corbalar",
    "mezeler": "soguk_mezeler",
    "baslangiclar": "salatalar",
    "atistirmaliklar": "ara_sicaklar",
    "deniz_urunleri": "deniz_urunleri",
    "ana_yemekler": "etler",
    "izgaralar": "etler",
    "steakhouse": "etler",
    "durum_ve_doner": "etler",
    "pideler": "makarnalar",
    "pizzalar": "makarnalar",
    "burgerler": "makarnalar",
    "meksika_mutfagi": "makarnalar",
    "cocuk_menusu": "makarnalar",
    "tatlilar": "tatlilar",
    "icecekler": "icecekler",
    "kokteyller": "icecekler",
}

BAND_OFFSET = {
    "ana_yemekler": 0,
    "izgaralar": 1,
    "steakhouse": 2,
    "durum_ve_doner": 3,
    "pideler": 0,
    "pizzalar": 1,
    "burgerler": 2,
    "meksika_mutfagi": 3,
    "cocuk_menusu": 4,
    "icecekler": 0,
    "kokteyller": 1,
}


def load_source_orders(source_cur) -> tuple[dict[str, int], dict[str, int]]:
    source_cur.execute(
        """
        SELECT slug, sort_order
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (SOURCE_MENU_ID,),
    )
    main_orders = {slug: sort_order for slug, sort_order in source_cur.fetchall()}

    source_cur.execute(
        """
        SELECT sc.slug, sc.sort_order
        FROM tbl_menu_sub_category sc
        WHERE sc.menu_id = %s AND sc.is_deleted = false
        ORDER BY sc.sort_order, sc.id
        """,
        (SOURCE_MENU_ID,),
    )
    sub_orders: dict[str, int] = {}
    for slug, sort_order in source_cur.fetchall():
        sub_orders.setdefault(slug, sort_order)
    return main_orders, sub_orders


def resolve_target_menu_id(target_cur) -> int | None:
    target_cur.execute(
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
    row = target_cur.fetchone()
    return row[0] if row else None


def resolve_main_sort(slug: str, source_main: dict[str, int]) -> int:
    if slug in source_main:
        return source_main[slug] * 10
    mapped = PARADISE_TO_AYA_SLUG.get(slug)
    if mapped is None or mapped not in source_main:
        return 999
    return source_main[mapped] * 10 + BAND_OFFSET.get(slug, 0)


def sync_menu(label: str, source_cfg: dict, target_cfg: dict) -> None:
    source_conn = psycopg2.connect(**source_cfg)
    target_conn = psycopg2.connect(**target_cfg)
    source_conn.autocommit = False
    target_conn.autocommit = False
    source_cur = source_conn.cursor()
    target_cur = target_conn.cursor()

    target_menu_id = resolve_target_menu_id(target_cur)
    if target_menu_id is None:
        print(f"[{label}] no menu for {TARGET_EMAIL}; skipped")
        source_cur.close()
        target_cur.close()
        source_conn.close()
        target_conn.close()
        return

    source_main, source_sub = load_source_orders(source_cur)
    now = datetime.utcnow()

    target_cur.execute(
        """
        SELECT id, slug, sort_order
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (target_menu_id,),
    )
    categories = target_cur.fetchall()
    main_updates = 0
    for category_id, slug, old_sort in categories:
        new_sort = resolve_main_sort(slug, source_main)
        if new_sort == old_sort:
            continue
        target_cur.execute(
            """
            UPDATE tbl_menu_category
            SET sort_order = %s, updated_at = %s
            WHERE id = %s
            """,
            (new_sort, now, category_id),
        )
        main_updates += 1
        print(f"[{label}] main {slug}: {old_sort} -> {new_sort}")

    target_cur.execute(
        """
        SELECT sc.id, sc.slug, sc.sort_order
        FROM tbl_menu_sub_category sc
        WHERE sc.menu_id = %s AND sc.is_deleted = false
        ORDER BY sc.menu_category_id, sc.sort_order, sc.id
        """,
        (target_menu_id,),
    )
    sub_updates = 0
    for sub_id, slug, old_sort in target_cur.fetchall():
        if slug not in source_sub:
            continue
        new_sort = source_sub[slug]
        if new_sort == old_sort:
            continue
        target_cur.execute(
            """
            UPDATE tbl_menu_sub_category
            SET sort_order = %s, updated_at = %s
            WHERE id = %s
            """,
            (new_sort, now, sub_id),
        )
        sub_updates += 1
        print(f"[{label}] sub {slug}: {old_sort} -> {new_sort}")

    target_conn.commit()
    print(f"[{label}] menu {target_menu_id}: updated {main_updates} main, {sub_updates} sub categories")

    target_cur.execute(
        """
        SELECT slug, name, sort_order
        FROM tbl_menu_category
        WHERE menu_id = %s AND is_deleted = false
        ORDER BY sort_order, id
        """,
        (target_menu_id,),
    )
    print(f"[{label}] final main order:")
    for slug, name, sort_order in target_cur.fetchall():
        print(f"  {sort_order:3} {slug}")

    source_cur.close()
    target_cur.close()
    source_conn.close()
    target_conn.close()


if __name__ == "__main__":
    sync_menu("PROD", PROD, PROD)
    sync_menu("STAGE", PROD, STAGE)
