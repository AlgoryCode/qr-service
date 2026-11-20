-- Konsol oturumu bitince muhasebeye otomatik gelir kaydı.
ALTER TABLE tbl_user_accounting_entry
    ADD COLUMN IF NOT EXISTS source_console_session_id BIGINT;

ALTER TABLE tbl_user_accounting_entry
    DROP CONSTRAINT IF EXISTS tbl_user_accounting_entry_source_type_check;
ALTER TABLE tbl_user_accounting_entry
    ADD CONSTRAINT tbl_user_accounting_entry_source_type_check
    CHECK (source_type IN ('MANUAL', 'BILL_SALE', 'BILL_TIP', 'ORDER_SALE', 'CONSOLE_SESSION'));

-- Bir oturum muhasebeye tek kez yazılır.
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_accounting_entry_console_session
    ON tbl_user_accounting_entry (source_console_session_id)
    WHERE source_console_session_id IS NOT NULL;
