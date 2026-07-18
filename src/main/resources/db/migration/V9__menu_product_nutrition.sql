ALTER TABLE tbl_menu_products
    ADD COLUMN IF NOT EXISTS nutrition jsonb;

UPDATE tbl_menu_products
SET nutrition = '{
  "basis": "PER_100G",
  "energyKj": 0,
  "energyKcal": 0,
  "fat": 0,
  "carbohydrate": 0,
  "fibre": 0,
  "protein": 0,
  "salt": 0,
  "vitaminsAndMinerals": [],
  "otherNutrients": []
}'::jsonb
WHERE nutrition IS NULL;

ALTER TABLE tbl_menu_products
    ALTER COLUMN nutrition SET NOT NULL;
