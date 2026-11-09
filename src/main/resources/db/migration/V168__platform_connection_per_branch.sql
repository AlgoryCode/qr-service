-- Each branch connects its own Uber Eats Trendyol Go and Yemeksepeti account; the merchant (işletme) owns them all.
-- At most one connection per branch and platform, plus at most one with no branch (the account-wide connection
-- created before per-branch connections, which keeps serving every branch).
-- The old uniqueness rule is a constraint on some databases and a plain index on others, so both forms are dropped.
ALTER TABLE ubereats_connections DROP CONSTRAINT IF EXISTS uk_ubereats_connections_user;
ALTER TABLE ubereats_connections DROP CONSTRAINT IF EXISTS uk_ubereats_connections_user_branch;
DROP INDEX IF EXISTS uk_ubereats_connections_user;
DROP INDEX IF EXISTS uk_ubereats_connections_user_branch;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ubereats_connections_merchant_branch
    ON ubereats_connections (merchant_id, COALESCE(branch_id, 0));
CREATE INDEX IF NOT EXISTS idx_ubereats_connections_merchant ON ubereats_connections (merchant_id);

ALTER TABLE yemeksepeti_connections DROP CONSTRAINT IF EXISTS uk_yemeksepeti_connections_user;
DROP INDEX IF EXISTS uk_yemeksepeti_connections_user;
CREATE UNIQUE INDEX IF NOT EXISTS uk_yemeksepeti_connections_merchant_branch
    ON yemeksepeti_connections (merchant_id, COALESCE(branch_id, 0));
CREATE INDEX IF NOT EXISTS idx_yemeksepeti_connections_merchant ON yemeksepeti_connections (merchant_id);
