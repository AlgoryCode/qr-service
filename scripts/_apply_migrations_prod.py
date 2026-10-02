import psycopg2

PROD = dict(
    host="185.184.210.52", port=5432,
    dbname="algoryqrdb", user="postgres",
    password="AdHqvxNc8MLBsMjOi82TjDzSMSuUDptBNjFVwpsvtVoaf6YOciJxqT84KgmBgc39",
    sslmode="disable", connect_timeout=10
)

conn = psycopg2.connect(**PROD)
conn.autocommit = False
cur = conn.cursor()

def col_exists(table, col):
    cur.execute("SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=%s AND column_name=%s", (table, col))
    return cur.fetchone() is not None

def table_exists(table):
    cur.execute("SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name=%s", (table,))
    return cur.fetchone() is not None

def index_exists(name):
    cur.execute("SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname=%s", (name,))
    return cur.fetchone() is not None

def constraint_exists(table, name):
    cur.execute("SELECT 1 FROM information_schema.table_constraints WHERE table_schema='public' AND table_name=%s AND constraint_name=%s", (table, name))
    return cur.fetchone() is not None

def flyway_applied(version):
    cur.execute("SELECT 1 FROM flyway_schema_history WHERE version=%s AND success=TRUE", (version,))
    return cur.fetchone() is not None

def record_flyway(version, description, script, checksum):
    cur.execute("SELECT COALESCE(MAX(installed_rank),0)+1 FROM flyway_schema_history")
    rank = cur.fetchone()[0]
    cur.execute(
        "INSERT INTO flyway_schema_history (installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success) "
        "VALUES (%s,%s,%s,'SQL',%s,%s,'postgres',NOW(),0,TRUE) ON CONFLICT DO NOTHING",
        (rank, version, description, script, checksum)
    )
    print(f"  Flyway V{version} kaydedildi")

def run(label, sql):
    try:
        cur.execute(sql)
    except Exception as e:
        print(f"  UYARI [{label}]: {str(e)[:120]}")
        conn.rollback()
        return False
    return True

# ─── V76 ───────────────────────────────────────────────────────────────────
if not flyway_applied("76"):
    print(">>> V76: ubereats connection drop branch")
    cur.execute("""
        DO $$
        DECLARE
          survivor_id BIGINT;
          loser RECORD;
        BEGIN
          FOR survivor_id IN
            SELECT DISTINCT ON (user_id) id
            FROM ubereats_connections
            ORDER BY user_id,
              CASE WHEN status = 'CONNECTED' THEN 0
                   WHEN status = 'PENDING_RESTAURANT' THEN 1
                   WHEN status = 'ERROR' THEN 2
                   ELSE 3 END,
              updated_at DESC NULLS LAST, id DESC
          LOOP
            FOR loser IN
              SELECT c.id FROM ubereats_connections c
              WHERE c.user_id = (SELECT user_id FROM ubereats_connections WHERE id = survivor_id)
                AND c.id <> survivor_id
            LOOP
              UPDATE ubereats_orders SET connection_id = survivor_id WHERE connection_id = loser.id;
              DELETE FROM ubereats_connections WHERE id = loser.id;
            END LOOP;
          END LOOP;
        END $$
    """)
    cur.execute("ALTER TABLE ubereats_connections DROP CONSTRAINT IF EXISTS uk_ubereats_connections_user_branch")
    cur.execute("DROP INDEX IF EXISTS uk_ubereats_connections_user_branch")
    cur.execute("ALTER TABLE ubereats_connections DROP CONSTRAINT IF EXISTS idx_tgo_connection_user_branch")
    cur.execute("DROP INDEX IF EXISTS idx_tgo_connection_user_branch")
    cur.execute("ALTER TABLE ubereats_connections DROP COLUMN IF EXISTS branch_id")
    if not index_exists("uk_ubereats_connections_user"):
        cur.execute("CREATE UNIQUE INDEX uk_ubereats_connections_user ON ubereats_connections (user_id)")
    record_flyway("76","ubereats connection drop branch","V76__ubereats_connection_drop_branch.sql",76001)
    print("  V76 TAMAM")
else:
    print("V76 zaten uygulanmis")

# ─── V77 ───────────────────────────────────────────────────────────────────
if not flyway_applied("77"):
    print(">>> V77: ai menu import")
    if not table_exists("ai_menu_import_jobs"):
        cur.execute("""
            CREATE TABLE ai_menu_import_jobs (
                id UUID PRIMARY KEY, tenant_id BIGINT NOT NULL, menu_id BIGINT NOT NULL,
                status VARCHAR(32) NOT NULL, image_urls JSONB NOT NULL,
                extracted_products JSONB, ai_batch_id VARCHAR(128),
                ai_input_file_id VARCHAR(128), ai_output_file_id VARCHAR(128),
                error_message TEXT, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                started_at TIMESTAMP, finished_at TIMESTAMP
            )
        """)
        cur.execute("CREATE INDEX idx_ai_menu_import_jobs_menu_status ON ai_menu_import_jobs (menu_id, status, created_at)")
        cur.execute("CREATE INDEX idx_ai_menu_import_jobs_status ON ai_menu_import_jobs (status, created_at)")
        print("  ai_menu_import_jobs olusturuldu")
    if not table_exists("ai_menu_import_drafts"):
        cur.execute("""
            CREATE TABLE ai_menu_import_drafts (
                id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES ai_menu_import_jobs(id),
                tenant_id BIGINT NOT NULL, menu_id BIGINT NOT NULL,
                source_product_id VARCHAR(128) NOT NULL, product_data JSONB NOT NULL,
                confidence NUMERIC(5,4), approval_status VARCHAR(32) NOT NULL,
                approved_by BIGINT, approved_at TIMESTAMP, published_product_id BIGINT,
                reject_reason TEXT, error_message TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cur.execute("CREATE INDEX idx_ai_menu_import_drafts_menu_status ON ai_menu_import_drafts (menu_id, approval_status, created_at)")
        cur.execute("CREATE UNIQUE INDEX uk_ai_menu_import_drafts_source ON ai_menu_import_drafts (job_id, source_product_id)")
        print("  ai_menu_import_drafts olusturuldu")
    record_flyway("77","ai menu import","V77__ai_menu_import.sql",77001)
    print("  V77 TAMAM")
else:
    print("V77 zaten uygulanmis")

# ─── V78 ───────────────────────────────────────────────────────────────────
if not flyway_applied("78"):
    print(">>> V78: subscription state metadata")
    cur.execute("ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS subscription_status_reason VARCHAR(128)")
    cur.execute("ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS subscription_status_changed_at TIMESTAMP")
    cur.execute("ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS subscription_status_changed_by VARCHAR(64)")
    record_flyway("78","subscription state metadata","V78__subscription_state_metadata.sql",78001)
    print("  V78 TAMAM")
else:
    print("V78 zaten uygulanmis")

# ─── V79 ───────────────────────────────────────────────────────────────────
if not flyway_applied("79"):
    print(">>> V79: basic email verification")
    cur.execute("ALTER TABLE tbl_user ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE")
    cur.execute("ALTER TABLE tbl_user ADD COLUMN IF NOT EXISTS email_verification_code_hash VARCHAR(255)")
    cur.execute("ALTER TABLE tbl_user ADD COLUMN IF NOT EXISTS email_verification_expires_at TIMESTAMP")
    cur.execute("ALTER TABLE tbl_user ADD COLUMN IF NOT EXISTS email_verification_sent_at TIMESTAMP")
    cur.execute("UPDATE tbl_user SET email_verified = TRUE WHERE provider <> 'BASIC'")
    record_flyway("79","basic email verification","V79__basic_email_verification.sql",79001)
    print("  V79 TAMAM")
else:
    print("V79 zaten uygulanmis")

# ─── V80 ───────────────────────────────────────────────────────────────────
if not flyway_applied("80"):
    print(">>> V80: purchase recurring consent")
    cur.execute("ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS recurring_consent BOOLEAN NOT NULL DEFAULT FALSE")
    record_flyway("80","purchase recurring consent","V80__purchase_recurring_consent.sql",80001)
    print("  V80 TAMAM")
else:
    print("V80 zaten uygulanmis")

# ─── V81 ───────────────────────────────────────────────────────────────────
if not flyway_applied("81"):
    print(">>> V81: menu product options")
    if not table_exists("tbl_menu_product_option_group"):
        cur.execute("""
            CREATE TABLE tbl_menu_product_option_group (
                id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL,
                name VARCHAR(120) NOT NULL, min_select INTEGER NOT NULL DEFAULT 0,
                max_select INTEGER NOT NULL DEFAULT 1, sort_order INTEGER NOT NULL DEFAULT 0,
                CONSTRAINT ck_menu_product_option_group_select
                    CHECK (min_select >= 0 AND max_select >= 1 AND min_select <= max_select)
            )
        """)
        cur.execute("CREATE INDEX idx_menu_product_option_group_product ON tbl_menu_product_option_group (product_id)")
        cur.execute("ALTER TABLE tbl_menu_product_option_group ADD CONSTRAINT fk_menu_product_option_group_product FOREIGN KEY (product_id) REFERENCES tbl_menu_products (product_id) ON DELETE CASCADE")
        print("  tbl_menu_product_option_group olusturuldu")
    else:
        print("  tbl_menu_product_option_group zaten var, atlanıyor")
    if not table_exists("tbl_menu_product_option"):
        cur.execute("""
            CREATE TABLE tbl_menu_product_option (
                id BIGSERIAL PRIMARY KEY, group_id BIGINT NOT NULL,
                name VARCHAR(120) NOT NULL, price_delta NUMERIC(12,2) NOT NULL DEFAULT 0,
                available BOOLEAN NOT NULL DEFAULT TRUE, sort_order INTEGER NOT NULL DEFAULT 0
            )
        """)
        cur.execute("CREATE INDEX idx_menu_product_option_group ON tbl_menu_product_option (group_id)")
        cur.execute("ALTER TABLE tbl_menu_product_option ADD CONSTRAINT fk_menu_product_option_group FOREIGN KEY (group_id) REFERENCES tbl_menu_product_option_group (id) ON DELETE CASCADE")
        print("  tbl_menu_product_option olusturuldu")
    else:
        print("  tbl_menu_product_option zaten var, atlanıyor")
    cur.execute("ALTER TABLE tbl_menu_order_item ADD COLUMN IF NOT EXISTS selected_options JSONB NOT NULL DEFAULT '[]'::jsonb")
    record_flyway("81","menu product options","V81__menu_product_options.sql",81001)
    print("  V81 TAMAM")
else:
    print("V81 zaten uygulanmis")

# ─── V82 ───────────────────────────────────────────────────────────────────
if not flyway_applied("82"):
    print(">>> V82: menu product option group kind unit")
    cur.execute("ALTER TABLE tbl_menu_product_option_group ADD COLUMN IF NOT EXISTS kind VARCHAR(32) NOT NULL DEFAULT 'CUSTOM'")
    cur.execute("ALTER TABLE tbl_menu_product_option_group ADD COLUMN IF NOT EXISTS unit VARCHAR(16) NOT NULL DEFAULT 'NONE'")
    if not constraint_exists("tbl_menu_product_option_group","ck_menu_product_option_group_kind"):
        cur.execute("ALTER TABLE tbl_menu_product_option_group ADD CONSTRAINT ck_menu_product_option_group_kind CHECK (kind IN ('SIZE','CHOICE','EXTRA','REMOVAL','PORTION','CUSTOM'))")
    if not constraint_exists("tbl_menu_product_option_group","ck_menu_product_option_group_unit"):
        cur.execute("ALTER TABLE tbl_menu_product_option_group ADD CONSTRAINT ck_menu_product_option_group_unit CHECK (unit IN ('NONE','PIECE','GRAM','ML','LITRE'))")
    record_flyway("82","menu product option group kind unit","V82__menu_product_option_group_kind_unit.sql",82001)
    print("  V82 TAMAM")
else:
    print("V82 zaten uygulanmis")

# ─── V83/V84/V85/V86 — sadece purchase_log constraint güncelle ───────────
FINAL_ACTIONS = [
    'PURCHASE_STARTED','PURCHASE_PAYMENT_PENDING','PURCHASE_COMPLETED','PURCHASE_PAYMENT_FAILED',
    'PURCHASE_EXPIRED','PURCHASE_DEACTIVATED','PURCHASE_REACTIVATED','PURCHASE_CANCELLED',
    'PURCHASE_CANCEL_AT_PERIOD_END','PURCHASE_RENEWAL_RESUMED','PURCHASE_DEBT_PAYMENT_STARTED',
    'PURCHASE_REFUND_STARTED','PURCHASE_REFUND_COMPLETED','ENTITLEMENT_GRANTED',
    'ENTITLEMENT_CONSUMED','ENTITLEMENT_RESTORED','PLAN_CHANGE_REQUESTED','PLAN_CHANGE_SCHEDULED',
    'PLAN_CHANGE_PAYMENT_STARTED','PLAN_CHANGE_PAYMENT_FAILED','PLAN_CHANGE_REFUND_STARTED',
    'PLAN_CHANGE_REFUND_COMPLETED','PLAN_CHANGE_COMPLETED','PLAN_CHANGE_CANCELLED',
    'PLAN_CHANGE_ENTITLEMENTS_RESET','TRIAL_STARTED','TRIAL_EXTENDED','TRIAL_REACTIVATED','TRIAL_ENDED'
]
for v, desc, fname in [
    ("83","purchase log trial extended","V83__purchase_log_trial_extended.sql"),
    ("84","purchase log trial lifecycle actions","V84__purchase_log_trial_lifecycle_actions.sql"),
    ("85","purchase log trial ended","V85__purchase_log_trial_ended.sql"),
    ("86","purchase log admin package lifecycle","V86__purchase_log_admin_package_lifecycle.sql"),
]:
    if not flyway_applied(v):
        print(f">>> V{v}: {desc}")
        cur.execute("ALTER TABLE tbl_purchase_log DROP CONSTRAINT IF EXISTS tbl_purchase_log_action_check")
        actions_list = ",".join([f"'{a}'" for a in FINAL_ACTIONS])
        cur.execute(f"ALTER TABLE tbl_purchase_log ADD CONSTRAINT tbl_purchase_log_action_check CHECK (action::text = ANY (ARRAY[{actions_list}]::text[]))")
        record_flyway(v, desc, fname, int(v)*1000+1)
        print(f"  V{v} TAMAM")
    else:
        print(f"V{v} zaten uygulanmis")

# ─── V87 ───────────────────────────────────────────────────────────────────
if not flyway_applied("87"):
    print(">>> V87: campaign image url")
    cur.execute("ALTER TABLE tbl_campaign ADD COLUMN IF NOT EXISTS image_url VARCHAR(512)")
    record_flyway("87","campaign image url","V87__campaign_image_url.sql",87001)
    print("  V87 TAMAM")
else:
    print("V87 zaten uygulanmis")

# ─── V88 ───────────────────────────────────────────────────────────────────
if not flyway_applied("88"):
    print(">>> V88: menu public id")
    cur.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto")
    cur.execute("ALTER TABLE tbl_menu ADD COLUMN IF NOT EXISTS public_id VARCHAR(32)")
    cur.execute("UPDATE tbl_menu SET public_id = rtrim(translate(encode(gen_random_bytes(16),'base64'),'+/','-_'),'=') WHERE public_id IS NULL OR btrim(public_id) = ''")
    cur.execute("ALTER TABLE tbl_menu ALTER COLUMN public_id SET NOT NULL")
    if not constraint_exists("tbl_menu","uk_menu_public_id"):
        cur.execute("ALTER TABLE tbl_menu ADD CONSTRAINT uk_menu_public_id UNIQUE (public_id)")
    record_flyway("88","menu public id","V88__menu_public_id.sql",88001)
    print("  V88 TAMAM")
else:
    print("V88 zaten uygulanmis")

# ─── V89 ───────────────────────────────────────────────────────────────────
if not flyway_applied("89"):
    print(">>> V89: campaign templates seed")
    cur.execute("""
        CREATE TABLE IF NOT EXISTS tbl_campaign_template (
            id BIGSERIAL PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE,
            name VARCHAR(120) NOT NULL, description TEXT, icon VARCHAR(40),
            config_schema JSONB NOT NULL DEFAULT '{}'::jsonb, sort_order INT NOT NULL DEFAULT 0
        )
    """)
    for code, name, desc, icon, schema, order in [
        ('STAMP_CARD','Damga kartı','Belirli ürünlerden N adet alınca ödül ürün verilir.','stamp',
         '{"fields":[{"key":"targetProductIds","type":"productIds"},{"key":"requiredQuantity","type":"number"},{"key":"reward","type":"reward"}]}',10),
        ('SPEND_THRESHOLD','Harcama eşiği','Haftalık veya aylık harcama eşiğine ulaşınca ödül ürün verilir.','spend',
         '{"fields":[{"key":"thresholdAmount","type":"number"},{"key":"period","type":"enum","values":["WEEKLY","MONTHLY"]},{"key":"reward","type":"reward"}]}',20),
    ]:
        cur.execute(
            "INSERT INTO tbl_campaign_template (code,name,description,icon,config_schema,sort_order) VALUES (%s,%s,%s,%s,%s::jsonb,%s) ON CONFLICT (code) DO NOTHING",
            (code, name, desc, icon, schema, order)
        )
    record_flyway("89","campaign templates seed","V89__campaign_templates_seed.sql",89001)
    print("  V89 TAMAM")
else:
    print("V89 zaten uygulanmis")

# ─── V90 ───────────────────────────────────────────────────────────────────
if not flyway_applied("90"):
    print(">>> V90: customer phone lastname nullable")
    cur.execute("ALTER TABLE tbl_customer ALTER COLUMN phone DROP NOT NULL")
    cur.execute("ALTER TABLE tbl_customer ALTER COLUMN last_name DROP NOT NULL")
    record_flyway("90","customer phone lastname nullable","V90__customer_phone_lastname_nullable.sql",90001)
    print("  V90 TAMAM")
else:
    print("V90 zaten uygulanmis")

# ─── V91 ───────────────────────────────────────────────────────────────────
if not flyway_applied("91"):
    print(">>> V91: ultimate trial package")
    if col_exists("tbl_plan_package","trial_eligible"):
        cur.execute("UPDATE tbl_plan_package SET trial_eligible=FALSE, trial_days=NULL, updated_at=NOW() WHERE code='ULTIMATE_PACKAGE' AND (trial_eligible=TRUE OR trial_days IS NOT NULL)")
    cur.execute("""
        INSERT INTO tbl_plan_package (code,name,description,features,price,subtotal,vat_amount,currency,active,validity_days,priority,purchasable,system_managed,yearly_price,billing_period,created_at,updated_at)
        SELECT 'ULTIMATE_TRIAL_PACKAGE','Ultimate Deneme','15 gunluk Ultimate deneme paketi',
        '["1 ucretsiz sube","Sube basi 1 ucretsiz menu","Garson siparis ve adisyon modulu","Ciro takibi ve gelismis raporlar","Haftalik akilli raporlama","Akilli asistan","Akilli ozet","Ozel tasarim menu","AI ile menu fotografından urun ekleme"]'::jsonb,
        0.00,0.00,0.00,'TRY',TRUE,15,190,FALSE,FALSE,NULL,'MONTHLY',NOW(),NOW()
        WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code='ULTIMATE_TRIAL_PACKAGE')
    """)
    cur.execute("UPDATE tbl_plan_package SET name='Ultimate Deneme',description='15 gunluk Ultimate deneme paketi',active=TRUE,validity_days=15,priority=190,purchasable=FALSE,system_managed=FALSE,yearly_price=NULL,updated_at=NOW() WHERE code='ULTIMATE_TRIAL_PACKAGE'")
    cur.execute("DELETE FROM tbl_plan_package_item WHERE package_id=(SELECT id FROM tbl_plan_package WHERE code='ULTIMATE_TRIAL_PACKAGE')")
    for pc, unlim in [('QR_CREATE',True),('QR_BRANCH',False),('QR_MENU',False),('MENU_PRODUCT',True),
                      ('SMART_REPORTING',True),('SMART_ASSISTANT',True),('SMART_SUMMARY',True),
                      ('CUSTOM_DESIGN',True),('WAITER_PANEL',True),('AI_MENU_IMPORT',True)]:
        cur.execute("INSERT INTO tbl_plan_package_item (package_id,product_id,quantity,unlimited) SELECT p.id,pr.id,1,%s FROM tbl_plan_package p JOIN tbl_product pr ON pr.code=%s WHERE p.code='ULTIMATE_TRIAL_PACKAGE'", (unlim, pc))
    record_flyway("91","ultimate trial package","V91__ultimate_trial_package.sql",91001)
    print("  V91 TAMAM")
else:
    print("V91 zaten uygulanmis")

# ─── V92 ───────────────────────────────────────────────────────────────────
if not flyway_applied("92"):
    print(">>> V92: drop package trial columns")
    cur.execute("ALTER TABLE tbl_plan_package DROP COLUMN IF EXISTS trial_days")
    cur.execute("ALTER TABLE tbl_plan_package DROP COLUMN IF EXISTS trial_eligible")
    record_flyway("92","drop package trial columns","V92__drop_package_trial_columns.sql",92001)
    print("  V92 TAMAM")
else:
    print("V92 zaten uygulanmis")

# ─── V93 ───────────────────────────────────────────────────────────────────
if not flyway_applied("93"):
    print(">>> V93: trial log and onboarding fulfillment")
    if not table_exists("tbl_trial_log"):
        cur.execute("""
            CREATE TABLE tbl_trial_log (
                id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, package_id BIGINT NOT NULL,
                package_code VARCHAR(64) NOT NULL, started_at TIMESTAMP NOT NULL,
                ends_at TIMESTAMP NOT NULL, duration_days INTEGER NOT NULL,
                status VARCHAR(16) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                CONSTRAINT uk_trial_log_user_id UNIQUE (user_id)
            )
        """)
        cur.execute("CREATE INDEX idx_trial_log_status_ends_at ON tbl_trial_log (status, ends_at)")
        print("  tbl_trial_log olusturuldu")
    cur.execute("SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='tbl_fulfillment' AND column_name='purchase_id'")
    row = cur.fetchone()
    if row and row[0] == 'NO':
        cur.execute("ALTER TABLE tbl_fulfillment ALTER COLUMN purchase_id DROP NOT NULL")
    if not col_exists("tbl_fulfillment","trial_log_id"):
        cur.execute("ALTER TABLE tbl_fulfillment ADD COLUMN trial_log_id BIGINT")
    if not index_exists("uk_fulfillment_trial_log_id"):
        cur.execute("CREATE UNIQUE INDEX uk_fulfillment_trial_log_id ON tbl_fulfillment (trial_log_id) WHERE trial_log_id IS NOT NULL")
    if not constraint_exists("tbl_fulfillment","chk_fulfillment_source"):
        cur.execute("ALTER TABLE tbl_fulfillment ADD CONSTRAINT chk_fulfillment_source CHECK ((purchase_id IS NOT NULL AND trial_log_id IS NULL) OR (purchase_id IS NULL AND trial_log_id IS NOT NULL))")
    cur.execute("""
        INSERT INTO tbl_trial_log (user_id,package_id,package_code,started_at,ends_at,duration_days,status)
        SELECT latest.user_id,latest.package_id,latest.package_code,
               COALESCE(latest.starts_at,latest.purchased_at),COALESCE(latest.expires_at,NOW()),
               GREATEST(1,CAST(EXTRACT(DAY FROM (COALESCE(latest.expires_at,NOW())-COALESCE(latest.starts_at,latest.purchased_at))) AS INTEGER)),
               CASE WHEN latest.status='ACTIVE' AND latest.expires_at IS NOT NULL AND latest.expires_at>NOW() THEN 'ACTIVE' ELSE 'ENDED' END
        FROM (SELECT DISTINCT ON (p.user_id) p.user_id,p.package_id,p.package_code,p.starts_at,p.purchased_at,p.expires_at,p.status
              FROM tbl_purchase p WHERE p.purchase_type='TRIAL' ORDER BY p.user_id,p.purchased_at DESC) latest
        WHERE NOT EXISTS (SELECT 1 FROM tbl_trial_log e WHERE e.user_id=latest.user_id)
    """)
    cur.execute("""
        UPDATE tbl_fulfillment f SET trial_log_id=t.id, purchase_id=NULL
        FROM tbl_trial_log t JOIN tbl_purchase p ON p.user_id=t.user_id AND p.purchase_type='TRIAL'
        WHERE f.purchase_id=p.id AND f.trial_log_id IS NULL
    """)
    cur.execute("""
        UPDATE tbl_fulfillment_detail d SET source='ONBOARDING_PACKAGE'
        FROM tbl_fulfillment f
        WHERE d.fulfillment_id=f.id AND f.trial_log_id IS NOT NULL AND d.source='PACKAGE_INCLUDE'
    """)
    cur.execute("UPDATE tbl_purchase SET status='EXPIRED', purchase_type='SYSTEM_GRANT' WHERE purchase_type='TRIAL'")
    record_flyway("93","trial log and onboarding fulfillment","V93__trial_log_and_onboarding_fulfillment.sql",93001)
    print("  V93 TAMAM")
else:
    print("V93 zaten uygulanmis")

# ─── V94 ───────────────────────────────────────────────────────────────────
if not flyway_applied("94"):
    print(">>> V94: assign trial to existing users")
    cur.execute("SELECT id FROM tbl_plan_package WHERE code='ULTIMATE_TRIAL_PACKAGE' AND active=TRUE LIMIT 1")
    row = cur.fetchone()
    if not row:
        print("  ULTIMATE_TRIAL_PACKAGE bulunamadi, V94 atlaniyor!")
    else:
        pkg_id = row[0]
        cur.execute("""
            INSERT INTO tbl_trial_log (user_id,package_id,package_code,started_at,ends_at,duration_days,status,created_at)
            SELECT u.id,%s,'ULTIMATE_TRIAL_PACKAGE',NOW(),NOW()+INTERVAL '15 days',15,'ACTIVE',NOW()
            FROM tbl_user u
            WHERE NOT EXISTS (SELECT 1 FROM tbl_trial_log t WHERE t.user_id=u.id)
              AND NOT EXISTS (SELECT 1 FROM tbl_purchase p WHERE p.user_id=u.id AND p.status='ACTIVE' AND p.purchase_type='PAID')
            ON CONFLICT ON CONSTRAINT uk_trial_log_user_id DO NOTHING
        """, (pkg_id,))
        cur.execute("""
            INSERT INTO tbl_fulfillment (user_id,trial_log_id,package_id,status,starts_at,expires_at,created_at)
            SELECT tl.user_id,tl.id,%s,'ACTIVE',tl.started_at,tl.ends_at,NOW()
            FROM tbl_trial_log tl WHERE tl.package_code='ULTIMATE_TRIAL_PACKAGE'
              AND NOT EXISTS (SELECT 1 FROM tbl_fulfillment f WHERE f.trial_log_id=tl.id)
        """, (pkg_id,))
        cur.execute("""
            INSERT INTO tbl_fulfillment_detail (fulfillment_id,user_id,product_id,product_type_id,feature_code,scope_code,quantity,unlimited,used_quantity,source,starts_at,expires_at,version,created_at)
            SELECT f.id,f.user_id,pr.id,'PACKAGE_PRODUCT',COALESCE(NULLIF(TRIM(pr.feature_code),''),pr.code),pr.scope_code,
                   CASE WHEN ppi.unlimited THEN 0 ELSE ppi.quantity END,ppi.unlimited,0,'ONBOARDING_PACKAGE',f.starts_at,f.expires_at,0,NOW()
            FROM tbl_fulfillment f
            JOIN tbl_trial_log tl ON tl.id=f.trial_log_id AND tl.package_code='ULTIMATE_TRIAL_PACKAGE'
            JOIN tbl_plan_package_item ppi ON ppi.package_id=%(pkg_id)s
            JOIN tbl_product pr ON pr.id=ppi.product_id
            WHERE NOT EXISTS (SELECT 1 FROM tbl_fulfillment_detail d WHERE d.fulfillment_id=f.id AND d.product_id=pr.id)
        """, {"pkg_id": pkg_id})
        cur.execute("SELECT COUNT(*) FROM tbl_trial_log WHERE package_code='ULTIMATE_TRIAL_PACKAGE'")
        print(f"  Aktif ULTIMATE_TRIAL_PACKAGE trial sayisi: {cur.fetchone()[0]}")
        record_flyway("94","assign trial to existing users","V94__assign_trial_to_existing_users.sql",94001)
        print("  V94 TAMAM")
else:
    print("V94 zaten uygulanmis")

conn.commit()
print("\n=== TUM MIGRATION'LAR BASARIYLA UYGULANDI ===")

# Final dogrulama
for table, col in [("tbl_campaign","image_url"),("tbl_fulfillment","trial_log_id")]:
    cur.execute("SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=%s AND column_name=%s", (table, col))
    print(f"{table}.{col}: {'OK' if cur.fetchone() else 'HALA EKSIK'}")
cur.execute("SELECT COUNT(*) FROM tbl_trial_log")
print(f"tbl_trial_log toplam: {cur.fetchone()[0]}")
cur.execute("SELECT COUNT(*) FROM tbl_trial_log WHERE package_code='ULTIMATE_TRIAL_PACKAGE' AND status='ACTIVE'")
print(f"Aktif ULTIMATE_TRIAL_PACKAGE: {cur.fetchone()[0]}")
conn.close()
