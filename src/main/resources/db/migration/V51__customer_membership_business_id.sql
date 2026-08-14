ALTER TABLE tbl_customer_membership
    ADD COLUMN IF NOT EXISTS business_id BIGINT;

UPDATE tbl_customer_membership cm
SET business_id = m.user_id
FROM tbl_menu m
WHERE cm.menu_id = m.menu_id
  AND cm.business_id IS NULL;

ALTER TABLE tbl_customer_membership
    ALTER COLUMN business_id SET NOT NULL;

ALTER TABLE tbl_customer_membership
    DROP CONSTRAINT IF EXISTS fk_customer_membership_menu;

ALTER TABLE tbl_customer_membership
    ADD CONSTRAINT fk_customer_membership_menu
        FOREIGN KEY (menu_id) REFERENCES tbl_menu (menu_id) ON DELETE RESTRICT;

ALTER TABLE tbl_customer_membership
    ADD CONSTRAINT fk_customer_membership_business
        FOREIGN KEY (business_id) REFERENCES tbl_user (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_customer_membership_business_id
    ON tbl_customer_membership (business_id);
