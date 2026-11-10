-- Per-branch receipt template: a heading, a paragraph under it and a closing footer.
-- Empty values fall back to the branch name and a plain thank-you line when the receipt is printed.
ALTER TABLE tbl_branch ADD COLUMN IF NOT EXISTS receipt_header VARCHAR(120);
ALTER TABLE tbl_branch ADD COLUMN IF NOT EXISTS receipt_intro VARCHAR(500);
ALTER TABLE tbl_branch ADD COLUMN IF NOT EXISTS receipt_footer VARCHAR(500);
