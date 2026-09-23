-- Staff / owner rename: tables and columns only (IDs unchanged).

ALTER TABLE IF EXISTS tbl_menu_waiter RENAME TO tbl_merchant_staff;
ALTER TABLE IF EXISTS tbl_merchant_staff RENAME COLUMN owner_user_id TO merchant_id;

ALTER TABLE IF EXISTS tbl_menu_waiter_session RENAME TO tbl_merchant_staff_session;
ALTER TABLE IF EXISTS tbl_merchant_staff_session RENAME COLUMN waiter_id TO staff_id;

ALTER TABLE IF EXISTS tbl_menu_order RENAME COLUMN waiter_id TO staff_id;
ALTER TABLE IF EXISTS tbl_menu_order RENAME COLUMN created_by_waiter_id TO created_by_staff_id;
ALTER TABLE IF EXISTS tbl_menu_order RENAME COLUMN cancelled_by_waiter_id TO cancelled_by_staff_id;

ALTER TABLE IF EXISTS tbl_table_bill RENAME COLUMN opened_by_waiter_id TO opened_by_staff_id;
ALTER TABLE IF EXISTS tbl_table_bill RENAME COLUMN closed_by_waiter_id TO closed_by_staff_id;

ALTER TABLE IF EXISTS tbl_table_bill_item RENAME COLUMN added_by_waiter_id TO added_by_staff_id;
ALTER TABLE IF EXISTS tbl_bill_payment RENAME COLUMN waiter_id TO staff_id;

ALTER TABLE IF EXISTS tbl_order_audit_log RENAME COLUMN waiter_id TO staff_id;
ALTER TABLE IF EXISTS tbl_bill_adjustment RENAME COLUMN waiter_id TO staff_id;

ALTER TABLE IF EXISTS tbl_waiter_commission_record RENAME TO tbl_merchant_staff_commission;
ALTER TABLE IF EXISTS tbl_merchant_staff_commission RENAME COLUMN waiter_id TO staff_id;

ALTER TABLE IF EXISTS tbl_work_shift RENAME COLUMN opened_by_waiter_id TO opened_by_staff_id;
ALTER TABLE IF EXISTS tbl_work_shift RENAME COLUMN closed_by_waiter_id TO closed_by_staff_id;

ALTER TABLE IF EXISTS tbl_work_shift_waiter RENAME TO tbl_work_shift_staff;
ALTER TABLE IF EXISTS tbl_work_shift_staff RENAME COLUMN waiter_id TO staff_id;

ALTER TABLE IF EXISTS tbl_user_accounting_entry RENAME COLUMN created_by_waiter_id TO created_by_staff_id;

ALTER TABLE IF EXISTS tbl_campaign_manual_grant RENAME COLUMN waiter_id TO staff_id;

ALTER TABLE IF EXISTS tbl_print_agent_device RENAME COLUMN owner_user_id TO merchant_id;
ALTER TABLE IF EXISTS tbl_print_pairing_code RENAME COLUMN owner_user_id TO merchant_id;
ALTER TABLE IF EXISTS tbl_print_job RENAME COLUMN owner_user_id TO merchant_id;
