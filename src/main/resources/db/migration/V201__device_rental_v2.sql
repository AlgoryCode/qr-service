-- Oyun & Cihaz Takip: konsol takibi her tür saatlik cihaza genişler (PC, VR, simülatör, bilardo...).
-- Tablo ve özellik adları (tbl_game_console, CONSOLE_RENTAL) korunur; yalnızca görünen adlar değişir.

-- Cihaz türü ve 4 kol ücreti.
ALTER TABLE tbl_game_console
    ADD COLUMN IF NOT EXISTS device_type VARCHAR(20) NOT NULL DEFAULT 'PLAYSTATION',
    ADD COLUMN IF NOT EXISTS multi_hourly_rate NUMERIC(12, 2);

ALTER TABLE tbl_game_console DROP CONSTRAINT IF EXISTS chk_game_console_multi_rate;
ALTER TABLE tbl_game_console
    ADD CONSTRAINT chk_game_console_multi_rate CHECK (multi_hourly_rate IS NULL OR multi_hourly_rate >= 0);

-- Şube ayarları: 4 kol varsayılanı, asgari süre, yuvarlama bloğu ve sadakat kuralı.
ALTER TABLE tbl_console_rate_setting
    ADD COLUMN IF NOT EXISTS default_multi_hourly_rate NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS minimum_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rounding_minutes INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS loyalty_every_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS loyalty_reward_minutes INTEGER;

ALTER TABLE tbl_console_rate_setting DROP CONSTRAINT IF EXISTS chk_console_rate_setting_rules;
ALTER TABLE tbl_console_rate_setting
    ADD CONSTRAINT chk_console_rate_setting_rules CHECK (
        (default_multi_hourly_rate IS NULL OR default_multi_hourly_rate >= 0)
        AND minimum_minutes BETWEEN 0 AND 1440
        AND rounding_minutes BETWEEN 1 AND 120
        AND (loyalty_every_minutes IS NULL OR loyalty_every_minutes > 0)
        AND (loyalty_reward_minutes IS NULL OR loyalty_reward_minutes > 0)
    );

-- Türe göre şube ücreti (ör. PC 40 TL, PlayStation 120 TL).
CREATE TABLE IF NOT EXISTS tbl_device_type_rate (
    id                 BIGSERIAL PRIMARY KEY,
    merchant_id        BIGINT         NOT NULL,
    branch_id          BIGINT         NOT NULL,
    device_type        VARCHAR(20)    NOT NULL,
    hourly_rate        NUMERIC(12, 2),
    multi_hourly_rate  NUMERIC(12, 2),
    created_at         TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted         BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_device_type_rate UNIQUE (branch_id, device_type),
    CONSTRAINT chk_device_type_rate CHECK (
        (hourly_rate IS NULL OR hourly_rate >= 0) AND (multi_hourly_rate IS NULL OR multi_hourly_rate >= 0)
    )
);

-- Saat dilimi tarifesi: seçili günlerde start..end arası ücrete yüzde ekler/düşer.
-- end_time <= start_time ise gece yarısını geçer (22:00-06:00).
CREATE TABLE IF NOT EXISTS tbl_device_tariff_rule (
    id              BIGSERIAL PRIMARY KEY,
    merchant_id     BIGINT         NOT NULL,
    branch_id       BIGINT         NOT NULL,
    name            VARCHAR(80)    NOT NULL,
    device_type     VARCHAR(20),
    days_mask       INTEGER        NOT NULL,
    start_time      TIME           NOT NULL,
    end_time        TIME           NOT NULL,
    adjust_percent  NUMERIC(6, 2)  NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_tariff_rule CHECK (
        days_mask BETWEEN 1 AND 127 AND adjust_percent BETWEEN -100 AND 500
    )
);

CREATE INDEX IF NOT EXISTS idx_device_tariff_rule_branch
    ON tbl_device_tariff_rule (branch_id, merchant_id)
    WHERE is_deleted = FALSE;

-- Müşteri kartı: ön ödemeli bakiye ve sadakat dakikaları (işletme genelinde).
CREATE TABLE IF NOT EXISTS tbl_device_customer (
    id                        BIGSERIAL PRIMARY KEY,
    merchant_id               BIGINT         NOT NULL,
    name                      VARCHAR(120)   NOT NULL,
    phone                     VARCHAR(32),
    note                      VARCHAR(500),
    balance                   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    free_minutes              INTEGER        NOT NULL DEFAULT 0,
    loyalty_progress_minutes  INTEGER        NOT NULL DEFAULT 0,
    total_minutes             INTEGER        NOT NULL DEFAULT 0,
    total_spent               NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at                TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted                BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_customer CHECK (
        balance >= 0 AND free_minutes >= 0 AND loyalty_progress_minutes >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_customer_phone
    ON tbl_device_customer (merchant_id, phone)
    WHERE phone IS NOT NULL AND is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_device_customer_merchant
    ON tbl_device_customer (merchant_id, name)
    WHERE is_deleted = FALSE;

-- Bakiye hareketleri: yükleme (TOPUP), oturum ödemesi (SPEND), düzeltme (ADJUST).
CREATE TABLE IF NOT EXISTS tbl_device_customer_txn (
    id           BIGSERIAL PRIMARY KEY,
    merchant_id  BIGINT         NOT NULL,
    customer_id  BIGINT         NOT NULL REFERENCES tbl_device_customer (id),
    txn_type     VARCHAR(16)    NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL,
    session_id   BIGINT,
    payment_method VARCHAR(16),
    note         VARCHAR(255),
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted   BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_device_customer_txn_customer
    ON tbl_device_customer_txn (customer_id, created_at DESC);

-- Rezervasyon: belirli saatte cihaz ayırma.
CREATE TABLE IF NOT EXISTS tbl_device_reservation (
    id             BIGSERIAL PRIMARY KEY,
    merchant_id    BIGINT         NOT NULL,
    branch_id      BIGINT         NOT NULL,
    console_id     BIGINT         NOT NULL REFERENCES tbl_game_console (id),
    customer_id    BIGINT,
    customer_name  VARCHAR(120)   NOT NULL,
    phone          VARCHAR(32),
    note           VARCHAR(500),
    starts_at      TIMESTAMP      NOT NULL,
    ends_at        TIMESTAMP      NOT NULL,
    status         VARCHAR(16)    NOT NULL DEFAULT 'BOOKED',
    session_id     BIGINT,
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted     BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_reservation CHECK (ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_device_reservation_branch
    ON tbl_device_reservation (branch_id, merchant_id, starts_at)
    WHERE is_deleted = FALSE;

-- Oturum: kol sayısı, planlanan süre, duraklatma, müşteri, ödeme ve tutar kırılımı.
ALTER TABLE tbl_console_session
    ADD COLUMN IF NOT EXISTS controller_count INTEGER NOT NULL DEFAULT 2,
    ADD COLUMN IF NOT EXISTS planned_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS customer_id BIGINT,
    ADD COLUMN IF NOT EXISTS reservation_id BIGINT,
    ADD COLUMN IF NOT EXISTS time_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS items_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS gross_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS loyalty_minutes_used INTEGER,
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(16),
    ADD COLUMN IF NOT EXISTS opened_by_staff_id BIGINT,
    ADD COLUMN IF NOT EXISTS closed_by_staff_id BIGINT;

-- Oyun aralıkları: duraklatma ve cihaz değiştirme oturumu parçalara böler, her parça kendi ücretiyle.
CREATE TABLE IF NOT EXISTS tbl_device_session_segment (
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT         NOT NULL REFERENCES tbl_console_session (id),
    console_id        BIGINT         NOT NULL,
    device_type       VARCHAR(20)    NOT NULL,
    hourly_rate       NUMERIC(12, 2) NOT NULL,
    controller_count  INTEGER        NOT NULL DEFAULT 2,
    started_at        TIMESTAMP      NOT NULL,
    ended_at          TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_device_session_segment_session
    ON tbl_device_session_segment (session_id, started_at);

-- Oturuma eklenen menü ürünleri (içecek, atıştırmalık); fiyat eklendiği anda sabitlenir.
CREATE TABLE IF NOT EXISTS tbl_device_session_item (
    id               BIGSERIAL PRIMARY KEY,
    merchant_id      BIGINT         NOT NULL,
    session_id       BIGINT         NOT NULL REFERENCES tbl_console_session (id),
    menu_product_id  BIGINT,
    name             VARCHAR(200)   NOT NULL,
    unit_price       NUMERIC(12, 2) NOT NULL,
    quantity         INTEGER        NOT NULL,
    line_total       NUMERIC(12, 2) NOT NULL,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted       BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_device_session_item CHECK (quantity > 0 AND unit_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_device_session_item_session
    ON tbl_device_session_item (session_id)
    WHERE is_deleted = FALSE;

-- Mevcut oturumlar: tek parça ve eski tutar kırılımı.
INSERT INTO tbl_device_session_segment (session_id, console_id, device_type, hourly_rate, controller_count, started_at, ended_at)
SELECT s.id, s.console_id, 'PLAYSTATION', s.hourly_rate, 2, s.started_at, s.ended_at
FROM tbl_console_session s
WHERE NOT EXISTS (SELECT 1 FROM tbl_device_session_segment g WHERE g.session_id = s.id);

UPDATE tbl_console_session
SET time_amount = amount, items_amount = 0, gross_amount = amount, discount_amount = 0
WHERE ended_at IS NOT NULL AND gross_amount IS NULL;

-- Görünen adlar.
UPDATE features
SET name = 'Oyun & cihaz takip',
    description = 'Konsol, PC, VR, simülatör, bilardo gibi cihazları saatlik kiralayın: oturum, rezervasyon, müşteri kartı ve raporlar.'
WHERE feature_key = 'CONSOLE_RENTAL';

UPDATE catalog_products
SET name = 'Oyun & Cihaz Takip',
    description = 'Saatlik cihaz kiralama: konsol, PC, VR, simülatör; tarifeler, rezervasyon, müşteri bakiyesi ve sadakat, raporlar'
WHERE code = 'CONSOLE_RENTAL';
