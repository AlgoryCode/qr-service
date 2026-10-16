-- Staff can be created before a branch is chosen.
ALTER TABLE tbl_merchant_staff
    ALTER COLUMN branch_id DROP NOT NULL;
