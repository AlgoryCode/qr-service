-- Online, Uber Eats ve Yemeksepeti siparişleri şube mutfağına bağlanır.
-- branch_id boş kalan kayıtlar eskisi gibi tüm şubelerde görünür.

ALTER TABLE store_orders ADD COLUMN IF NOT EXISTS branch_id BIGINT;

UPDATE store_orders o
SET branch_id = m.branch_id
FROM merchants m
WHERE o.merchant_id = m.id AND o.branch_id IS NULL AND m.branch_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_store_orders_merchant_branch_status ON store_orders (merchant_id, branch_id, status);

ALTER TABLE ubereats_connections ADD COLUMN IF NOT EXISTS branch_id BIGINT;
ALTER TABLE yemeksepeti_connections ADD COLUMN IF NOT EXISTS branch_id BIGINT;

WITH single_branch AS (
    SELECT user_id, MIN(id) AS branch_id
    FROM tbl_branch
    WHERE COALESCE(is_deleted, false) = false
    GROUP BY user_id
    HAVING COUNT(*) = 1
)
UPDATE ubereats_connections c
SET branch_id = s.branch_id
FROM single_branch s
WHERE c.user_id = s.user_id AND c.branch_id IS NULL;

WITH single_branch AS (
    SELECT user_id, MIN(id) AS branch_id
    FROM tbl_branch
    WHERE COALESCE(is_deleted, false) = false
    GROUP BY user_id
    HAVING COUNT(*) = 1
)
UPDATE yemeksepeti_connections c
SET branch_id = s.branch_id
FROM single_branch s
WHERE c.user_id = s.user_id AND c.branch_id IS NULL;
