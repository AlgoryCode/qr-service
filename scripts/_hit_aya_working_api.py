import json
import urllib.request

BASE = "https://prod.qrapi.algorycode.com"
PUBLIC_ID = "scxtG9KR5FrK0RlLjcYPUw"
MENU_ID = 16
QR_ID = 35


def get(path: str):
    with urllib.request.urlopen(BASE + path, timeout=30) as resp:
        return resp.status, json.load(resp)


print("DB public_id:", PUBLIC_ID)
print()

status, data = get(f"/menu/public/id/{QR_ID}")
menu = data.get("menu") or {}
print(f"OK {status} /menu/public/id/{QR_ID}")
print("  business:", menu.get("businessName"))
print("  menuId:", menu.get("menuId"), "qrId:", menu.get("qrId"))
print("  bootstrap products:", len(data.get("products") or []))

status, products = get(f"/menu/public/{MENU_ID}/products?page=0&size=10")
content = products.get("content") or []
print(f"\nOK {status} /menu/public/{MENU_ID}/products")
print("  totalElements:", products.get("totalElements"), "hasNext:", products.get("hasNext"))
for p in content[:10]:
    print(f"  - {p.get('productId')}: {p.get('name')} | {p.get('price')} | {p.get('mainCategoryName')}")

status, cats = get(f"/menu/public/{MENU_ID}/categories?page=0&size=20")
print(f"\nOK {status} /menu/public/{MENU_ID}/categories")
for c in (cats.get("content") or [])[:12]:
    print(f"  - {c.get('name')}")

print("\nNew publicId routes on THIS prod image: not mapped (still old menuId/qrId API).")
print("Frontend calling /menu/public/{publicId} or legacy-qr => 500 type-mismatch / missing route.")
