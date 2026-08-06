import json
from pathlib import Path

root = Path(r"c:\Users\guven\OneDrive\Desktop\Services\qr-service")
doc = json.loads((root / "src/main/resources/seed/menu-taxonomy.json").read_text(encoding="utf-8"))
lines = []

def esc(value: str) -> str:
    return value.replace("'", "''")

for m in doc["mains"]:
    lines.append(
        "INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) "
        f"VALUES ({m['id']}, '{m['slug']}', '{esc(m['name'])}', {m['sortOrder']}, NOW(), NOW(), FALSE) "
        "ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, "
        "updated_at=NOW(), is_deleted=FALSE;"
    )
    for s in m["subs"]:
        lines.append(
            "INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) "
            f"VALUES ({s['id']}, {m['id']}, '{s['slug']}', '{esc(s['name'])}', {s['sortOrder']}, NOW(), NOW(), FALSE) "
            "ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, "
            "name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;"
        )

for t in doc["tags"]:
    lines.append(
        "INSERT INTO tbl_menu_tag (id, slug, name, sort_order, created_at, updated_at, is_deleted) "
        f"VALUES ({t['id']}, '{t['slug']}', '{esc(t['name'])}', {t['sortOrder']}, NOW(), NOW(), FALSE) "
        "ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, "
        "updated_at=NOW(), is_deleted=FALSE;"
    )

lines.extend(
    [
        "",
        "UPDATE tbl_menu_products p",
        "SET sub_category_id = s.id",
        "FROM tbl_sub_category s",
        "WHERE p.sub_category_id IS NULL",
        "  AND p.is_deleted = FALSE",
        "  AND s.is_deleted = FALSE",
        "  AND p.category IS NOT NULL",
        "  AND LOWER(TRIM(p.category)) = LOWER(TRIM(s.name));",
        "",
        "UPDATE tbl_menu_products p",
        "SET sub_category_id = s.id",
        "FROM tbl_menu_category c",
        "JOIN tbl_sub_category s ON LOWER(TRIM(c.name)) = LOWER(TRIM(s.name)) AND s.is_deleted = FALSE",
        "WHERE p.sub_category_id IS NULL",
        "  AND p.is_deleted = FALSE",
        "  AND p.category_id = c.category_id",
        "  AND c.is_deleted = FALSE;",
        "",
        "UPDATE tbl_menu_products",
        "SET sub_category_id = 45",
        "WHERE sub_category_id IS NULL;",
        "",
        "ALTER TABLE tbl_menu_products ALTER COLUMN sub_category_id SET NOT NULL;",
        "",
        "ALTER TABLE tbl_menu_products DROP CONSTRAINT IF EXISTS fk_menu_product_sub_category;",
        "ALTER TABLE tbl_menu_products",
        "    ADD CONSTRAINT fk_menu_product_sub_category",
        "        FOREIGN KEY (sub_category_id) REFERENCES tbl_sub_category (id);",
        "",
        "ALTER TABLE tbl_menu_products DROP CONSTRAINT IF EXISTS fk_menu_product_category;",
        "ALTER TABLE tbl_menu_products DROP COLUMN IF EXISTS category_id;",
        "ALTER TABLE tbl_menu_products DROP COLUMN IF EXISTS category;",
        "",
    ]
)

out = root / "src/main/resources/db/migration/V21__menu_taxonomy_and_tags.sql"
base = out.read_text(encoding="utf-8")
out.write_text(base + "\n" + "\n".join(lines), encoding="utf-8")
print(f"appended {len(lines)} lines")
