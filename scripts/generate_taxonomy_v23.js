/**
 * Writes V23 taxonomy delta (new mains/subs/tags) as Flyway upsert SQL.
 * Run: node scripts/generate_taxonomy_v23.js
 */
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const doc = JSON.parse(
  fs.readFileSync(path.join(root, "src/main/resources/seed/menu-taxonomy.json"), "utf8")
);
const esc = (s) => s.replace(/'/g, "''");

const NEW_MAIN_IDS = new Set([20, 21, 22]);
const NEW_SUB_IDS = new Set([66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81]);
const NEW_TAG_IDS = new Set([7, 8, 9]);

const lines = ["-- Taxonomy expansion (new mains/subs/tags). Idempotent upsert by id."];

for (const m of doc.mains) {
  if (NEW_MAIN_IDS.has(m.id)) {
    lines.push(
      `INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (${m.id}, '${m.slug}', '${esc(m.name)}', ${m.sortOrder}, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;`
    );
  }
  for (const s of m.subs) {
    if (NEW_SUB_IDS.has(s.id) || NEW_MAIN_IDS.has(m.id)) {
      lines.push(
        `INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (${s.id}, ${m.id}, '${s.slug}', '${esc(s.name)}', ${s.sortOrder}, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;`
      );
    }
  }
}

for (const t of doc.tags) {
  if (NEW_TAG_IDS.has(t.id)) {
    lines.push(
      `INSERT INTO tbl_menu_tag (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (${t.id}, '${t.slug}', '${esc(t.name)}', ${t.sortOrder}, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;`
    );
  }
}

const out = path.join(root, "src/main/resources/db/migration/V23__taxonomy_expansion.sql");
fs.writeFileSync(out, lines.join("\n") + "\n");
console.log("Wrote", out, "lines", lines.length - 1);
