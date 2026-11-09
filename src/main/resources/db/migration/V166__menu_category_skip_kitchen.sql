-- "Mutfağa gitmez": bu kategorideki ürünler mutfak fişine düşmez, garson siparişinde doğrudan adisyona yazılır.
ALTER TABLE tbl_menu_category ADD COLUMN IF NOT EXISTS skip_kitchen BOOLEAN NOT NULL DEFAULT FALSE;
