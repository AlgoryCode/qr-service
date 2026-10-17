"""
Keep only service roots under com.ael.algoryqrservice:
  authservice, fulfillmentservice, qrservice (+ bootstrap in parent package).

All former top-level modules (billing, menu, model, …) live under qrservice.
"""

from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = "com.ael.algoryqrservice"
SERVICE_ROOTS = frozenset({"authservice", "fulfillmentservice", "qrservice"})
TEXT_SUFFIXES = {".java", ".md", ".yaml", ".yml", ".xml", ".properties", ".sql", ".kt"}

PACKAGE_PREFIX = re.compile(
    r"\bcom\.ael\.algoryqrservice\.(?!authservice(?:\.|$)|fulfillmentservice(?:\.|$)|qrservice(?:\.|$))"
)


def remap_reference(text: str) -> str:
    return PACKAGE_PREFIX.sub(f"{BASE}.qrservice.", text)


def move_modules(java_root: Path) -> None:
    qr_root = java_root / "qrservice"
    qr_root.mkdir(exist_ok=True)
    for child in list(java_root.iterdir()):
        if not child.is_dir():
            continue
        if child.name in SERVICE_ROOTS:
            continue
        dest = qr_root / child.name
        if dest.exists():
            raise SystemExit(f"target already exists: {dest}")
        shutil.move(str(child), str(dest))
        print(f"moved {child.name} -> qrservice/{child.name}")


def rewrite_tree(base: Path) -> int:
    updated = 0
    for path in base.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            original = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        text = remap_reference(original)
        if text != original:
            path.write_text(text, encoding="utf-8")
            updated += 1
    return updated


def main() -> None:
    for java_root in (
        ROOT / "src" / "main" / "java" / "com" / "ael" / "algoryqrservice",
        ROOT / "src" / "test" / "java" / "com" / "ael" / "algoryqrservice",
    ):
        if java_root.is_dir():
            move_modules(java_root)

    count = 0
    for scan in (ROOT / "src", ROOT / "docs", ROOT / "scripts"):
        if scan.exists():
            count += rewrite_tree(scan)
    print(f"rewrote {count} files")


if __name__ == "__main__":
    main()
