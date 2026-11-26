-- Oyun & Cihaz Takip: süresi aşılan / unutulan oturum uyarısı ve biten oturumu düzeltme geçmişi. Tekrar çalıştırılabilir.

-- Arka plan işi süresini aşan açık oturumu işaretler; panel bu işarete göre uyarı gösterir.
ALTER TABLE tbl_console_session
    ADD COLUMN IF NOT EXISTS overdue_at    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS overdue_reason VARCHAR(24),
    ADD COLUMN IF NOT EXISTS edited_at     TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_console_session_overdue
    ON tbl_console_session (merchant_id)
    WHERE overdue_at IS NOT NULL AND ended_at IS NULL AND is_deleted = FALSE;

-- Biten oturumda yapılan her düzeltme: kim, ne zaman, neden, ne değişti.
CREATE TABLE IF NOT EXISTS tbl_console_session_change (
    id          BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT        NOT NULL,
    session_id  BIGINT        NOT NULL,
    staff_id    BIGINT,
    changed_by  VARCHAR(160),
    reason      VARCHAR(255)  NOT NULL,
    summary     VARCHAR(2000) NOT NULL,
    old_amount  NUMERIC(12, 2),
    new_amount  NUMERIC(12, 2),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_console_session_change_session
    ON tbl_console_session_change (session_id, created_at DESC) WHERE is_deleted = FALSE;
