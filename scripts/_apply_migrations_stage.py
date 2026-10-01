import psycopg2
import hashlib
import sys

DB = dict(
    host="185.184.210.52", port=5433,
    dbname="algoryqrdb-stage", user="postgres",
    password="postgres_stage", sslmode="disable", connect_timeout=10
)

conn = psycopg2.connect(**DB)
conn.autocommit = False
cur = conn.cursor()

def col_exists(table, col):
    cur.execute(
        "SELECT 1 FROM information_schema.columns "
        "WHERE table_schema='public' AND table_name=%s AND column_name=%s",
        (table, col)
    )
    return cur.fetchone() is not None

def table_exists(table):
    cur.execute(
        "SELECT 1 FROM information_schema.tables "
        "WHERE table_schema='public' AND table_name=%s",
        (table,)
    )
    return cur.fetchone() is not None

def constraint_exists(table, name):
    cur.execute(
        "SELECT 1 FROM information_schema.table_constraints "
        "WHERE table_schema='public' AND table_name=%s AND constraint_name=%s",
        (table, name)
    )
    return cur.fetchone() is not None

def index_exists(name):
    cur.execute(
        "SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname=%s",
        (name,)
    )
    return cur.fetchone() is not None

def flyway_applied(version):
    cur.execute(
        "SELECT 1 FROM flyway_schema_history WHERE version=%s AND success=TRUE",
        (version,)
    )
    return cur.fetchone() is not None

def record_flyway(version, description, script, checksum):
    cur.execute("SELECT COALESCE(MAX(installed_rank),0)+1 FROM flyway_schema_history")
    rank = cur.fetchone()[0]
    cur.execute(
        "INSERT INTO flyway_schema_history "
        "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) "
        "VALUES (%s,%s,%s,'SQL',%s,%s,'postgres',NOW(),0,TRUE) "
        "ON CONFLICT DO NOTHING",
        (rank, version, description, script, checksum)
    )

print("=== DURUM ===")
print("tbl_trial_log:", table_exists("tbl_trial_log"))
print("tbl_fulfillment.trial_log_id:", col_exists("tbl_fulfillment", "trial_log_id"))
print("tbl_fulfillment.purchase_id nullable?")
cur.execute(
    "SELECT is_nullable FROM information_schema.columns "
    "WHERE table_schema='public' AND table_name='tbl_fulfillment' AND column_name='purchase_id'"
)
row = cur.fetchone()
print(" ", row[0] if row else "KOLOM YOK")
print("V91 Flyway:", flyway_applied("91"))
print("V92 Flyway:", flyway_applied("92"))
print("V93 Flyway:", flyway_applied("93"))
print("V94 Flyway:", flyway_applied("94"))

# --- V91 ---
if not flyway_applied("91"):
    print("\n>>> V91 uygulanıyor...")
    # trial_eligible/trial_days kolonlari yoksa UPDATE'i atla
    if col_exists("tbl_plan_package", "trial_eligible"):
        cur.execute("""
            UPDATE tbl_plan_package
            SET trial_eligible = FALSE, trial_days = NULL, updated_at = NOW()
            WHERE code = 'ULTIMATE_PACKAGE'
              AND (trial_eligible = TRUE OR trial_days IS NOT NULL)
        """)

    cur.execute("""
        INSERT INTO tbl_plan_package (
            code, name, description, features,
            price, subtotal, vat_amount, currency,
            active, validity_days, priority,
            purchasable, system_managed, yearly_price,
            billing_period, created_at, updated_at
        )
        SELECT
            'ULTIMATE_TRIAL_PACKAGE',
            'Ultimate Deneme',
            '15 gunluk Ultimate deneme paketi',
            '["1 ucretsiz sube","Sube basi 1 ucretsiz menu","Garson siparis ve adisyon modulu","Ciro takibi ve gelismis raporlar","Haftalik akilli raporlama","Akilli asistan","Akilli ozet","Ozel tasarim menu","AI ile menu fotografından urun ekleme"]'::jsonb,
            0.00, 0.00, 0.00, 'TRY',
            TRUE, 15, 190,
            FALSE, FALSE, NULL,
            'MONTHLY', NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM tbl_plan_package WHERE code = 'ULTIMATE_TRIAL_PACKAGE'
        )
    """)

    cur.execute("""
        UPDATE tbl_plan_package
        SET name = 'Ultimate Deneme',
            description = '15 gunluk Ultimate deneme paketi',
            active = TRUE,
            validity_days = 15,
            priority = 190,
            purchasable = FALSE,
            system_managed = FALSE,
            yearly_price = NULL,
            updated_at = NOW()
        WHERE code = 'ULTIMATE_TRIAL_PACKAGE'
    """)

    # Package items
    cur.execute("""
        DELETE FROM tbl_plan_package_item
        WHERE package_id = (SELECT id FROM tbl_plan_package WHERE code = 'ULTIMATE_TRIAL_PACKAGE')
    """)

    for product_code, unlimited in [
        ('QR_CREATE', True), ('QR_BRANCH', False), ('QR_MENU', False),
        ('MENU_PRODUCT', True), ('SMART_REPORTING', True), ('SMART_ASSISTANT', True),
        ('SMART_SUMMARY', True), ('CUSTOM_DESIGN', True), ('WAITER_PANEL', True),
        ('AI_MENU_IMPORT', True)
    ]:
        cur.execute("""
            INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
            SELECT p.id, pr.id, 1, %s
            FROM tbl_plan_package p
            JOIN tbl_product pr ON pr.code = %s
            WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE'
        """, (unlimited, product_code))

    record_flyway("91", "ultimate trial package", "V91__ultimate_trial_package.sql", 91001)
    print("V91 TAMAM")
else:
    print("V91 zaten uygulanmis, atlaniyor")

# --- V92 ---
if not flyway_applied("92"):
    print("\n>>> V92 uygulanıyor...")
    cur.execute("ALTER TABLE tbl_plan_package DROP COLUMN IF EXISTS trial_days")
    cur.execute("ALTER TABLE tbl_plan_package DROP COLUMN IF EXISTS trial_eligible")
    record_flyway("92", "drop package trial columns", "V92__drop_package_trial_columns.sql", 92001)
    print("V92 TAMAM")
else:
    print("V92 zaten uygulanmis, atlaniyor")

# --- V93 ---
if not flyway_applied("93"):
    print("\n>>> V93 uygulanıyor...")

    if not table_exists("tbl_trial_log"):
        cur.execute("""
            CREATE TABLE tbl_trial_log (
                id             BIGSERIAL PRIMARY KEY,
                user_id        BIGINT       NOT NULL,
                package_id     BIGINT       NOT NULL,
                package_code   VARCHAR(64)  NOT NULL,
                started_at     TIMESTAMP    NOT NULL,
                ends_at        TIMESTAMP    NOT NULL,
                duration_days  INTEGER      NOT NULL,
                status         VARCHAR(16)  NOT NULL,
                created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
                CONSTRAINT uk_trial_log_user_id UNIQUE (user_id)
            )
        """)
        cur.execute("CREATE INDEX idx_trial_log_status_ends_at ON tbl_trial_log (status, ends_at)")
        print("  tbl_trial_log olusturuldu")

    # purchase_id nullable yap
    cur.execute("""
        SELECT is_nullable FROM information_schema.columns
        WHERE table_schema='public' AND table_name='tbl_fulfillment' AND column_name='purchase_id'
    """)
    row = cur.fetchone()
    if row and row[0] == 'NO':
        cur.execute("ALTER TABLE tbl_fulfillment ALTER COLUMN purchase_id DROP NOT NULL")
        print("  purchase_id NOT NULL kaldirildi")

    # trial_log_id ekle
    if not col_exists("tbl_fulfillment", "trial_log_id"):
        cur.execute("ALTER TABLE tbl_fulfillment ADD COLUMN trial_log_id BIGINT")
        print("  trial_log_id kolonu eklendi")

    if not index_exists("uk_fulfillment_trial_log_id"):
        cur.execute("""
            CREATE UNIQUE INDEX uk_fulfillment_trial_log_id
            ON tbl_fulfillment (trial_log_id)
            WHERE trial_log_id IS NOT NULL
        """)

    if not constraint_exists("tbl_fulfillment", "chk_fulfillment_source"):
        cur.execute("""
            ALTER TABLE tbl_fulfillment
            ADD CONSTRAINT chk_fulfillment_source
                CHECK (
                    (purchase_id IS NOT NULL AND trial_log_id IS NULL)
                    OR (purchase_id IS NULL AND trial_log_id IS NOT NULL)
                )
        """)

    # eski TRIAL purchase'lari tbl_trial_log'a tası
    cur.execute("""
        INSERT INTO tbl_trial_log (
            user_id, package_id, package_code,
            started_at, ends_at, duration_days, status
        )
        SELECT
            latest.user_id,
            latest.package_id,
            latest.package_code,
            COALESCE(latest.starts_at, latest.purchased_at),
            COALESCE(latest.expires_at, NOW()),
            GREATEST(1, CAST(EXTRACT(DAY FROM (
                COALESCE(latest.expires_at, NOW()) - COALESCE(latest.starts_at, latest.purchased_at)
            )) AS INTEGER)),
            CASE
                WHEN latest.status = 'ACTIVE' AND latest.expires_at IS NOT NULL AND latest.expires_at > NOW()
                    THEN 'ACTIVE'
                ELSE 'ENDED'
            END
        FROM (
            SELECT DISTINCT ON (p.user_id)
                p.user_id, p.package_id, p.package_code,
                p.starts_at, p.purchased_at, p.expires_at, p.status
            FROM tbl_purchase p
            WHERE p.purchase_type = 'TRIAL'
            ORDER BY p.user_id, p.purchased_at DESC
        ) latest
        WHERE NOT EXISTS (
            SELECT 1 FROM tbl_trial_log e WHERE e.user_id = latest.user_id
        )
    """)
    cur.execute("SELECT COUNT(*) FROM tbl_trial_log")
    print("  tbl_trial_log kayit sayisi:", cur.fetchone()[0])

    # fulfillment'lari guncelle
    cur.execute("""
        UPDATE tbl_fulfillment f
        SET trial_log_id = t.id, purchase_id = NULL
        FROM tbl_trial_log t
        JOIN tbl_purchase p
            ON p.user_id = t.user_id AND p.purchase_type = 'TRIAL'
        WHERE f.purchase_id = p.id AND f.trial_log_id IS NULL
    """)

    cur.execute("""
        UPDATE tbl_fulfillment_detail d
        SET source = 'ONBOARDING_PACKAGE'
        FROM tbl_fulfillment f
        WHERE d.fulfillment_id = f.id
          AND f.trial_log_id IS NOT NULL
          AND d.source = 'PACKAGE_INCLUDE'
    """)

    cur.execute("""
        UPDATE tbl_purchase
        SET status = 'EXPIRED', purchase_type = 'SYSTEM_GRANT'
        WHERE purchase_type = 'TRIAL'
    """)

    record_flyway("93", "trial log and onboarding fulfillment", "V93__trial_log_and_onboarding_fulfillment.sql", 93001)
    print("V93 TAMAM")
else:
    print("V93 zaten uygulanmis, atlaniyor")

# --- V94 ---
if not flyway_applied("94"):
    print("\n>>> V94 uygulanıyor...")
    cur.execute("""
        SELECT id FROM tbl_plan_package
        WHERE code = 'ULTIMATE_TRIAL_PACKAGE' AND active = TRUE
        LIMIT 1
    """)
    row = cur.fetchone()
    if not row:
        print("  ULTIMATE_TRIAL_PACKAGE bulunamadi, V94 atlaniyor!")
    else:
        pkg_id = row[0]
        cur.execute("""
            INSERT INTO tbl_trial_log (
                user_id, package_id, package_code,
                started_at, ends_at, duration_days, status, created_at
            )
            SELECT
                u.id, %s, 'ULTIMATE_TRIAL_PACKAGE',
                NOW(), NOW() + INTERVAL '15 days', 15, 'ACTIVE', NOW()
            FROM tbl_user u
            WHERE NOT EXISTS (SELECT 1 FROM tbl_trial_log t WHERE t.user_id = u.id)
              AND NOT EXISTS (
                  SELECT 1 FROM tbl_purchase p
                  WHERE p.user_id = u.id AND p.status = 'ACTIVE' AND p.purchase_type = 'PAID'
              )
            ON CONFLICT ON CONSTRAINT uk_trial_log_user_id DO NOTHING
        """, (pkg_id,))
        cur.execute("SELECT COUNT(*) FROM tbl_trial_log WHERE package_code='ULTIMATE_TRIAL_PACKAGE'")
        print("  ULTIMATE_TRIAL_PACKAGE trial log sayisi:", cur.fetchone()[0])

        cur.execute("""
            INSERT INTO tbl_fulfillment (
                user_id, trial_log_id, package_id,
                status, starts_at, expires_at, created_at
            )
            SELECT
                tl.user_id, tl.id, %s,
                'ACTIVE', tl.started_at, tl.ends_at, NOW()
            FROM tbl_trial_log tl
            WHERE tl.package_code = 'ULTIMATE_TRIAL_PACKAGE'
              AND NOT EXISTS (
                  SELECT 1 FROM tbl_fulfillment f WHERE f.trial_log_id = tl.id
              )
        """, (pkg_id,))

        cur.execute("""
            INSERT INTO tbl_fulfillment_detail (
                fulfillment_id, user_id, product_id,
                product_type_id, feature_code, scope_code,
                quantity, unlimited, used_quantity,
                source, starts_at, expires_at, version, created_at
            )
            SELECT
                f.id, f.user_id, pr.id,
                'PACKAGE_PRODUCT',
                COALESCE(NULLIF(TRIM(pr.feature_code), ''), pr.code),
                pr.scope_code,
                CASE WHEN ppi.unlimited THEN 0 ELSE ppi.quantity END,
                ppi.unlimited, 0,
                'ONBOARDING_PACKAGE',
                f.starts_at, f.expires_at, 0, NOW()
            FROM tbl_fulfillment f
            JOIN tbl_trial_log tl
                ON tl.id = f.trial_log_id AND tl.package_code = 'ULTIMATE_TRIAL_PACKAGE'
            JOIN tbl_plan_package_item ppi ON ppi.package_id = %s
            JOIN tbl_product pr ON pr.id = ppi.product_id
            WHERE NOT EXISTS (
                SELECT 1 FROM tbl_fulfillment_detail d
                WHERE d.fulfillment_id = f.id AND d.product_id = pr.id
            )
        """, (pkg_id,))

        record_flyway("94", "assign trial to existing users", "V94__assign_trial_to_existing_users.sql", 94001)
        print("V94 TAMAM")
else:
    print("V94 zaten uygulanmis, atlaniyor")

conn.commit()
print("\n=== TUM MIGRATION'LAR BASARIYLA UYGULANDY ===")

# Final durum
cur.execute("SELECT COUNT(*) FROM tbl_trial_log")
print("tbl_trial_log toplam:", cur.fetchone()[0])
cur.execute("SELECT COUNT(*) FROM tbl_trial_log WHERE package_code='ULTIMATE_TRIAL_PACKAGE' AND status='ACTIVE'")
print("Aktif ULTIMATE_TRIAL_PACKAGE trial:", cur.fetchone()[0])
cur.execute("SELECT COUNT(*) FROM tbl_fulfillment WHERE trial_log_id IS NOT NULL")
print("trial_log_id olan fulfillment:", cur.fetchone()[0])

conn.close()
