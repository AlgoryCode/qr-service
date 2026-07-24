ALTER TABLE tbl_purchase
    ADD COLUMN current_period_conversation_id VARCHAR(128);

UPDATE tbl_purchase
SET current_period_conversation_id = payment_conversation_id
WHERE current_period_conversation_id IS NULL
  AND payment_conversation_id IS NOT NULL;

ALTER TABLE tbl_purchase
    ADD COLUMN refund_status VARCHAR(24) NOT NULL DEFAULT 'NONE';

ALTER TABLE tbl_purchase
    ADD CONSTRAINT chk_purchase_refund_status
        CHECK (refund_status IN ('NONE', 'PENDING', 'COMPLETED', 'NEEDS_RECONCILE'));
