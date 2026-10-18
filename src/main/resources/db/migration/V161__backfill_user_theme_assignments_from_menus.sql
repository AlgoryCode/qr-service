-- Backfill member theme assignments from the themes their live menus already use.
-- Without this, GET /menu/themes returns an empty list for every existing member and
-- MenuThemeEnforcementAspect rejects menu updates that keep the member's current theme.
-- Soft-deleted menus are ignored: only themes in active use are granted.
-- Idempotent; safe to re-run on stage/prod while Flyway remains disabled.

INSERT INTO tbl_user_theme_assignment (merchant_id, theme_id, assigned_by, assigned_at)
SELECT DISTINCT m.merchant_id, t.id, NULL::BIGINT, NOW()
FROM tbl_menu m
JOIN tbl_menu_theme t ON LOWER(t.code) = LOWER(TRIM(m.theme_id))
WHERE COALESCE(m.is_deleted, FALSE) = FALSE
  AND t.active = TRUE
ON CONFLICT (merchant_id, theme_id) DO NOTHING;
