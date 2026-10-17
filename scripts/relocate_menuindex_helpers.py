#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

JAVA_ROOT = Path(__file__).resolve().parents[1] / "src/main/java/com/ael/algoryqrservice"
TARGET_PKG = "com.ael.algoryqrservice.product.menuindex.menuproductreindexservice.service"
SRC_DIR = JAVA_ROOT / "product/service/menuindex"


def main() -> None:
    if not SRC_DIR.is_dir():
        return
    import_map = {}
    rel_pkg = TARGET_PKG.removeprefix("com.ael.algoryqrservice.")
    dest = JAVA_ROOT.joinpath(*rel_pkg.split("."))
    dest.mkdir(parents=True, exist_ok=True)
    for path in SRC_DIR.glob("*.java"):
        if path.stem.endswith("Service"):
            continue
        text = path.read_text(encoding="utf-8")
        old_pkg = re.search(r"^package\s+([\w.]+);", text, re.M).group(1)
        class_name = path.stem
        text = re.sub(r"^package\s+[\w.]+;", f"package {TARGET_PKG};", text, count=1, flags=re.M)
        (dest / path.name).write_text(text, encoding="utf-8")
        import_map[f"{old_pkg}.{class_name}"] = f"{TARGET_PKG}.{class_name}"
        path.unlink()
    for jpath in JAVA_ROOT.rglob("*.java"):
        text = jpath.read_text(encoding="utf-8")
        for old, new in sorted(import_map.items(), key=lambda x: len(x[0]), reverse=True):
            text = text.replace(old, new)
        jpath.write_text(text, encoding="utf-8")
    if SRC_DIR.exists() and not any(SRC_DIR.iterdir()):
        SRC_DIR.rmdir()
    print(f"Moved {len(import_map)} menuindex helpers")


if __name__ == "__main__":
    main()
