-- Social media links on the branch and on the menu, plus per-menu show/hide flags.
-- The public menu shows a platform when its link is set (branch link first, then the
-- menu's own link) and the menu's show_* flag is on.

ALTER TABLE tbl_branch
    ADD COLUMN IF NOT EXISTS facebook_url  VARCHAR(512),
    ADD COLUMN IF NOT EXISTS instagram_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS youtube_url   VARCHAR(512),
    ADD COLUMN IF NOT EXISTS tiktok_url    VARCHAR(512),
    ADD COLUMN IF NOT EXISTS x_url         VARCHAR(512);

ALTER TABLE tbl_menu
    ADD COLUMN IF NOT EXISTS facebook_url   VARCHAR(512),
    ADD COLUMN IF NOT EXISTS instagram_url  VARCHAR(512),
    ADD COLUMN IF NOT EXISTS youtube_url    VARCHAR(512),
    ADD COLUMN IF NOT EXISTS tiktok_url     VARCHAR(512),
    ADD COLUMN IF NOT EXISTS x_url          VARCHAR(512),
    ADD COLUMN IF NOT EXISTS show_facebook  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS show_instagram BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS show_youtube   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS show_tiktok    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS show_x         BOOLEAN NOT NULL DEFAULT TRUE;
