#!/usr/bin/env node
/**
 * Export public menu categories + products from prod/stage into demo-algory-menu.json.
 *
 * Usage:
 *   DEMO_EXPORT_BASE=https://prod.qrapi.algorycode.com \
 *   DEMO_EXPORT_IYB=<publicId> DEMO_EXPORT_AYA=<publicId> \
 *   node scripts/export-demo-menu-seed.mjs
 *
 * Or:
 *   node scripts/export-demo-menu-seed.mjs --base https://prod.qrapi.algorycode.com --iyb ID --aya ID
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, "..");
const outPath = path.join(root, "src/main/resources/seed/demo-algory-menu.json");

function parseArgs(argv) {
  const args = { base: process.env.DEMO_EXPORT_BASE || "https://prod.qrapi.algorycode.com" };
  for (let i = 2; i < argv.length; i++) {
    const key = argv[i];
    if (key === "--base") args.base = argv[++i];
    else if (key === "--iyb") args.iyb = argv[++i];
    else if (key === "--aya") args.aya = argv[++i];
    else if (key === "--out") args.out = argv[++i];
  }
  args.iyb = args.iyb || process.env.DEMO_EXPORT_IYB;
  args.aya = args.aya || process.env.DEMO_EXPORT_AYA;
  return args;
}

async function fetchJson(url) {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`${response.status} ${url}: ${text.slice(0, 200)}`);
  }
  return response.json();
}

function slugify(prefix, slug) {
  const base = (slug || "genel").trim().toLowerCase().replace(/[^a-z0-9_]+/g, "_");
  return `${prefix}_${base}`.replace(/_+/g, "_");
}

async function loadSource(base, prefix, publicId) {
  if (!publicId) return null;
  const categoriesUrl = `${base.replace(/\/$/, "")}/menu/public/${encodeURIComponent(publicId)}/categories?page=0&size=50`;
  const catPage = await fetchJson(categoriesUrl);
  const mains = catPage.content || [];
  const categories = [];
  const products = [];
  let productSort = 1;

  for (const main of mains) {
    const mainSlug = slugify(prefix, main.slug || main.name);
    const subs = main.subs || [];
    const subEntries = subs.map((sub, idx) => ({
      slug: slugify(prefix, sub.slug || sub.name),
      name: sub.name,
      sortOrder: sub.sortOrder ?? idx + 1,
    }));
    categories.push({
      slug: mainSlug,
      name: main.name,
      sortOrder: main.sortOrder ?? categories.length + 1,
      subs: subEntries,
    });

    for (const sub of subs) {
      let page = 0;
      let hasNext = true;
      while (hasNext) {
        const productsUrl = `${base.replace(/\/$/, "")}/menu/public/${encodeURIComponent(publicId)}/products?page=${page}&size=50&subCategoryId=${sub.id}`;
        const productPage = await fetchJson(productsUrl);
        const content = productPage.content || [];
        for (const item of content) {
          products.push({
            name: item.name,
            description: item.description || undefined,
            price: item.price != null ? Number(item.price) : undefined,
            currency: item.currency || "TRY",
            subCategorySlug: slugify(prefix, item.subCategorySlug || sub.slug || sub.name),
            tagSlugs: [],
            allergenSlugs: [],
            sortOrder: productSort++,
            available: item.available !== false,
            imageUrl: item.imageUrl || undefined,
          });
        }
        hasNext = productPage.hasNext === true;
        page += 1;
        if (page > 40) break;
      }
    }
  }

  return { categories, products };
}

async function main() {
  const args = parseArgs(process.argv);
  if (!args.iyb && !args.aya) {
    console.error("Set DEMO_EXPORT_IYB and/or DEMO_EXPORT_AYA (public menu identifiers).");
    process.exit(1);
  }

  const mergedCategories = [];
  const mergedProducts = [];
  for (const [prefix, id] of [
    ["iyb", args.iyb],
    ["aya", args.aya],
  ]) {
    if (!id) continue;
    console.log(`Exporting ${prefix} from ${id}...`);
    const chunk = await loadSource(args.base, prefix, id);
    if (!chunk) continue;
    mergedCategories.push(...chunk.categories);
    mergedProducts.push(...chunk.products);
  }

  const document = {
    version: 1,
    themeId: "lilas-doux",
    branch: {
      address: "Caferağa Mah. Moda Cad. No:42 Kadıköy / İstanbul",
      phone: "+90 216 555 01 42",
      email: "demo@algorycafe.local",
    },
    categories: mergedCategories,
    products: mergedProducts,
  };

  const target = args.out ? path.resolve(args.out) : outPath;
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, `${JSON.stringify(document, null, 2)}\n`, "utf8");
  console.log(`Wrote ${mergedProducts.length} products, ${mergedCategories.length} main categories → ${target}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
