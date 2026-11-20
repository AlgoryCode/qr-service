-- Oyun & Cihaz Takip, 3. adım: QR'dan durum, paketler, oyun kütüphanesi, bakım ve kârlılık, vardiya,
-- üyelik, seviye, hediye bakiye ve arkadaş getir, turnuva. Tekrar çalıştırılabilir.

-- ---- Cihaz: QR anahtarı, bakım durumu, alış maliyeti ------------------------------------------------------------
ALTER TABLE tbl_game_console
    ADD COLUMN IF NOT EXISTS public_token VARCHAR(40),
    ADD COLUMN IF NOT EXISTS maintenance BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS maintenance_note VARCHAR(255),
    ADD COLUMN IF NOT EXISTS maintenance_since TIMESTAMP,
    ADD COLUMN IF NOT EXISTS purchase_cost NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS purchased_on DATE;

UPDATE tbl_game_console
SET public_token = substr(md5(random()::text || id::text || clock_timestamp()::text), 1, 24)
WHERE public_token IS NULL;

ALTER TABLE tbl_game_console ALTER COLUMN public_token SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_game_console_public_token ON tbl_game_console (public_token);

ALTER TABLE tbl_game_console DROP CONSTRAINT IF EXISTS chk_game_console_purchase_cost;
ALTER TABLE tbl_game_console
    ADD CONSTRAINT chk_game_console_purchase_cost CHECK (purchase_cost IS NULL OR purchase_cost >= 0);

-- ---- Oyun kütüphanesi ------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tbl_device_game (
    id           BIGSERIAL PRIMARY KEY,
    merchant_id  BIGINT       NOT NULL,
    branch_id    BIGINT       NOT NULL,
    name         VARCHAR(120) NOT NULL,
    genre        VARCHAR(60),
    max_players  INTEGER,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_game_players CHECK (max_players IS NULL OR max_players BETWEEN 1 AND 64)
);

CREATE INDEX IF NOT EXISTS idx_device_game_branch ON tbl_device_game (branch_id) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS tbl_device_console_game (
    console_id BIGINT NOT NULL REFERENCES tbl_game_console (id),
    game_id    BIGINT NOT NULL REFERENCES tbl_device_game (id),
    PRIMARY KEY (console_id, game_id)
);

CREATE INDEX IF NOT EXISTS idx_device_console_game_game ON tbl_device_console_game (game_id);

-- ---- Paketler: sabit fiyatlı süre (ör. 3 saat 250 TL, gece paketi) --------------------------------------------
CREATE TABLE IF NOT EXISTS tbl_device_package (
    id           BIGSERIAL PRIMARY KEY,
    merchant_id  BIGINT         NOT NULL,
    branch_id    BIGINT         NOT NULL,
    name         VARCHAR(80)    NOT NULL,
    device_type  VARCHAR(20),
    minutes      INTEGER        NOT NULL,
    price        NUMERIC(12, 2) NOT NULL,
    days_mask    INTEGER        NOT NULL DEFAULT 127,
    start_time   TIME,
    end_time     TIME,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted   BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_package CHECK (
        minutes BETWEEN 1 AND 1440
        AND price >= 0
        AND days_mask BETWEEN 1 AND 127
        AND ((start_time IS NULL AND end_time IS NULL) OR (start_time IS NOT NULL AND end_time IS NOT NULL))
    )
);

CREATE INDEX IF NOT EXISTS idx_device_package_branch ON tbl_device_package (branch_id) WHERE is_deleted = FALSE;

-- ---- Bakım ve masraf kayıtları ---------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tbl_device_maintenance_log (
    id           BIGSERIAL PRIMARY KEY,
    merchant_id  BIGINT         NOT NULL,
    branch_id    BIGINT         NOT NULL,
    console_id   BIGINT         NOT NULL REFERENCES tbl_game_console (id),
    kind         VARCHAR(16)    NOT NULL,
    note         VARCHAR(255),
    cost         NUMERIC(12, 2),
    started_at   TIMESTAMP      NOT NULL,
    ended_at     TIMESTAMP,
    staff_id     BIGINT,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted   BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_maintenance_log CHECK (
        kind IN ('REPAIR', 'CONTROLLER', 'EXPENSE', 'OTHER')
        AND (cost IS NULL OR cost >= 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_device_maintenance_log_console
    ON tbl_device_maintenance_log (console_id, started_at DESC);

-- ---- Müşteri programı: seviye, hediye bakiye, arkadaş getir, son ziyaret ---------------------------------------
ALTER TABLE tbl_device_customer
    ADD COLUMN IF NOT EXISTS bonus_balance NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS referral_code VARCHAR(16),
    ADD COLUMN IF NOT EXISTS referred_by_id BIGINT,
    ADD COLUMN IF NOT EXISTS referral_rewarded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_visit_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS visits INTEGER NOT NULL DEFAULT 0;

ALTER TABLE tbl_device_customer DROP CONSTRAINT IF EXISTS chk_device_customer_bonus;
ALTER TABLE tbl_device_customer ADD CONSTRAINT chk_device_customer_bonus CHECK (bonus_balance >= 0);

UPDATE tbl_device_customer
SET referral_code = upper(substr(md5(random()::text || id::text || clock_timestamp()::text), 1, 8))
WHERE referral_code IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_customer_referral
    ON tbl_device_customer (merchant_id, referral_code)
    WHERE referral_code IS NOT NULL;

UPDATE tbl_device_customer c
SET last_visit_at = v.last_visit, visits = v.visits
FROM (
    SELECT customer_id, MAX(ended_at) AS last_visit, COUNT(*) AS visits
    FROM tbl_console_session
    WHERE customer_id IS NOT NULL AND ended_at IS NOT NULL AND is_deleted = FALSE
    GROUP BY customer_id
) v
WHERE v.customer_id = c.id AND c.last_visit_at IS NULL;

-- Hangi personelin hangi şubede işlem yaptığı (vardiya kasası için).
ALTER TABLE tbl_device_customer_txn
    ADD COLUMN IF NOT EXISTS branch_id BIGINT,
    ADD COLUMN IF NOT EXISTS staff_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_device_customer_txn_branch
    ON tbl_device_customer_txn (branch_id, created_at)
    WHERE branch_id IS NOT NULL;

-- İşletme geneli program ayarları (müşteri kartları tüm şubelerde ortak).
CREATE TABLE IF NOT EXISTS tbl_device_program_setting (
    id                       BIGSERIAL PRIMARY KEY,
    merchant_id              BIGINT         NOT NULL UNIQUE,
    referral_reward_amount   NUMERIC(12, 2),
    referral_welcome_amount  NUMERIC(12, 2),
    created_at               TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted               BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_program_setting CHECK (
        (referral_reward_amount IS NULL OR referral_reward_amount >= 0)
        AND (referral_welcome_amount IS NULL OR referral_welcome_amount >= 0)
    )
);

CREATE TABLE IF NOT EXISTS tbl_device_tier (
    id                BIGSERIAL PRIMARY KEY,
    merchant_id       BIGINT         NOT NULL,
    name              VARCHAR(40)    NOT NULL,
    min_spent         NUMERIC(12, 2) NOT NULL,
    discount_percent  NUMERIC(5, 2)  NOT NULL,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_tier CHECK (min_spent >= 0 AND discount_percent BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_device_tier_merchant ON tbl_device_tier (merchant_id) WHERE is_deleted = FALSE;

-- ---- Üyelik --------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tbl_device_membership_plan (
    id           BIGSERIAL PRIMARY KEY,
    merchant_id  BIGINT         NOT NULL,
    name         VARCHAR(80)    NOT NULL,
    price        NUMERIC(12, 2) NOT NULL,
    minutes      INTEGER        NOT NULL,
    period_days  INTEGER        NOT NULL DEFAULT 30,
    device_type  VARCHAR(20),
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted   BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_membership_plan CHECK (
        price >= 0 AND minutes BETWEEN 1 AND 100000 AND period_days BETWEEN 1 AND 366
    )
);

CREATE TABLE IF NOT EXISTS tbl_device_membership (
    id              BIGSERIAL PRIMARY KEY,
    merchant_id     BIGINT         NOT NULL,
    branch_id       BIGINT,
    customer_id     BIGINT         NOT NULL REFERENCES tbl_device_customer (id),
    plan_id         BIGINT         NOT NULL REFERENCES tbl_device_membership_plan (id),
    plan_name       VARCHAR(80)    NOT NULL,
    device_type     VARCHAR(20),
    minutes_total   INTEGER        NOT NULL,
    minutes_left    INTEGER        NOT NULL,
    price           NUMERIC(12, 2) NOT NULL,
    payment_method  VARCHAR(16)    NOT NULL,
    starts_at       TIMESTAMP      NOT NULL,
    ends_at         TIMESTAMP      NOT NULL,
    cancelled       BOOLEAN        NOT NULL DEFAULT FALSE,
    staff_id        BIGINT,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_membership CHECK (minutes_left >= 0 AND minutes_left <= minutes_total AND ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_device_membership_customer ON tbl_device_membership (customer_id, ends_at);
CREATE INDEX IF NOT EXISTS idx_device_membership_branch ON tbl_device_membership (branch_id, created_at);

-- ---- Vardiya -------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tbl_device_shift (
    id             BIGSERIAL PRIMARY KEY,
    merchant_id    BIGINT         NOT NULL,
    branch_id      BIGINT         NOT NULL,
    staff_id       BIGINT,
    opened_at      TIMESTAMP      NOT NULL,
    closed_at      TIMESTAMP,
    opening_cash   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    expected_cash  NUMERIC(12, 2),
    counted_cash   NUMERIC(12, 2),
    note           VARCHAR(500),
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted     BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_shift CHECK (opening_cash >= 0 AND (counted_cash IS NULL OR counted_cash >= 0))
);

-- Kişi başına tek açık vardiya (personel yoksa işletme sahibi).
CREATE UNIQUE INDEX IF NOT EXISTS uq_device_shift_open
    ON tbl_device_shift (branch_id, COALESCE(staff_id, 0))
    WHERE closed_at IS NULL AND is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_device_shift_branch ON tbl_device_shift (branch_id, opened_at DESC);

-- ---- Turnuva -------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tbl_device_tournament (
    id                BIGSERIAL PRIMARY KEY,
    merchant_id       BIGINT         NOT NULL,
    branch_id         BIGINT         NOT NULL,
    name              VARCHAR(120)   NOT NULL,
    game_name         VARCHAR(120),
    starts_at         TIMESTAMP      NOT NULL,
    entry_fee         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    prize_note        VARCHAR(255),
    max_players       INTEGER        NOT NULL,
    status            VARCHAR(16)    NOT NULL DEFAULT 'REGISTRATION',
    winner_player_id  BIGINT,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_tournament CHECK (
        entry_fee >= 0
        AND max_players BETWEEN 2 AND 128
        AND status IN ('REGISTRATION', 'RUNNING', 'FINISHED', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_device_tournament_branch ON tbl_device_tournament (branch_id, starts_at DESC);

CREATE TABLE IF NOT EXISTS tbl_device_tournament_player (
    id              BIGSERIAL PRIMARY KEY,
    tournament_id   BIGINT       NOT NULL REFERENCES tbl_device_tournament (id),
    customer_id     BIGINT,
    name            VARCHAR(120) NOT NULL,
    phone           VARCHAR(32),
    paid            BOOLEAN      NOT NULL DEFAULT FALSE,
    payment_method  VARCHAR(16),
    paid_at         TIMESTAMP,
    staff_id        BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_device_tournament_player ON tbl_device_tournament_player (tournament_id);

CREATE TABLE IF NOT EXISTS tbl_device_tournament_match (
    id             BIGSERIAL PRIMARY KEY,
    tournament_id  BIGINT      NOT NULL REFERENCES tbl_device_tournament (id),
    round_no       INTEGER     NOT NULL,
    slot_no        INTEGER     NOT NULL,
    player1_id     BIGINT,
    player2_id     BIGINT,
    winner_id      BIGINT,
    score          VARCHAR(40),
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    is_deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_device_tournament_match UNIQUE (tournament_id, round_no, slot_no)
);

-- ---- Oturum: paket, oyun, üyelik, seviye, hediye bakiye, QR'dan süre isteği -----------------------------------
ALTER TABLE tbl_console_session
    ADD COLUMN IF NOT EXISTS package_id BIGINT,
    ADD COLUMN IF NOT EXISTS package_name VARCHAR(80),
    ADD COLUMN IF NOT EXISTS package_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS package_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS game_id BIGINT,
    ADD COLUMN IF NOT EXISTS game_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS membership_id BIGINT,
    ADD COLUMN IF NOT EXISTS membership_minutes_used INTEGER,
    ADD COLUMN IF NOT EXISTS tier_discount_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS bonus_used NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS extension_request_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS extension_requested_at TIMESTAMP;

-- ---- Muhasebe: üyelik satışı ve turnuva katılımı ---------------------------------------------------------------
ALTER TABLE tbl_user_accounting_entry
    ADD COLUMN IF NOT EXISTS source_device_ref VARCHAR(40);

ALTER TABLE tbl_user_accounting_entry
    DROP CONSTRAINT IF EXISTS tbl_user_accounting_entry_source_type_check;
ALTER TABLE tbl_user_accounting_entry
    ADD CONSTRAINT tbl_user_accounting_entry_source_type_check
    CHECK (source_type IN ('MANUAL', 'BILL_SALE', 'BILL_TIP', 'ORDER_SALE', 'CONSOLE_SESSION', 'DEVICE_SALE'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_accounting_entry_device_ref
    ON tbl_user_accounting_entry (source_device_ref)
    WHERE source_device_ref IS NOT NULL;
