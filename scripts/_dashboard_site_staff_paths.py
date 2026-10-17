import os

ROOT = r"C:\Users\Tarik\Desktop\Services\algoryqr-dashboard-site"
API_OLD = os.path.join(ROOT, "src", "app", "api", "waiter-panel")
API_NEW = os.path.join(ROOT, "src", "app", "api", "staff", "panel")

REPLACEMENTS = [
    ("/api/waiter-panel", "/api/staff/panel"),
    ('"/waiter-panel', '"/staff/panel'),
    ("'/waiter-panel", "'/staff/panel"),
    ("`/waiter-panel", "`/staff/panel"),
]


def main() -> None:
    if os.path.isdir(API_OLD):
        os.makedirs(os.path.dirname(API_NEW), exist_ok=True)
        if os.path.isdir(API_NEW):
            raise SystemExit(f"target already exists: {API_NEW}")
        os.rename(API_OLD, API_NEW)
        print("moved api route folder to staff/panel")

    src = os.path.join(ROOT, "src")
    updated = 0
    for dirpath, _, files in os.walk(src):
        for fn in files:
            if not fn.endswith((".ts", ".tsx", ".js", ".jsx", ".md")):
                continue
            path = os.path.join(dirpath, fn)
            with open(path, encoding="utf-8") as f:
                content = f.read()
            new = content
            for old, new_part in REPLACEMENTS:
                new = new.replace(old, new_part)
            if new != content:
                with open(path, "w", encoding="utf-8", newline="\n") as f:
                    f.write(new)
                updated += 1
    print(f"updated {updated} files")


if __name__ == "__main__":
    main()
