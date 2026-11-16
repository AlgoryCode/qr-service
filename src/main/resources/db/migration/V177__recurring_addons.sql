-- Recurring modules bought after the package: their full-period price is added to the host
-- purchase's renewal charge. Kept apart from recurring_price (package + modules bought with it).
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS addon_recurring_amount NUMERIC(12, 2);
