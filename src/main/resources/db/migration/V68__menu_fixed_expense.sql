CREATE TABLE tbl_menu_fixed_expense (
    id BIGSERIAL PRIMARY KEY,
    menu_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    daily_amount NUMERIC(12, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_menu_fixed_expense_menu FOREIGN KEY (menu_id) REFERENCES tbl_menu(menu_id) ON DELETE CASCADE,
    CONSTRAINT chk_menu_fixed_expense_daily_amount CHECK (daily_amount >= 0.01)
);

CREATE INDEX idx_menu_fixed_expense_menu ON tbl_menu_fixed_expense (menu_id, active);
