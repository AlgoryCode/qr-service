#!/usr/bin/env python3
"""Move menu/qr product types into com.ael.algoryqrservice.product.*"""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java/com/ael/algoryqrservice"

MOVES: list[tuple[Path, Path, str]] = [
    (JAVA_ROOT / "qr/dto/ProductRequest.java", JAVA_ROOT / "product/dto/ProductRequest.java", "com.ael.algoryqrservice.product.dto"),
    (JAVA_ROOT / "qr/dto/ProductResponse.java", JAVA_ROOT / "product/dto/ProductResponse.java", "com.ael.algoryqrservice.product.dto"),
    (JAVA_ROOT / "menu/dto/MenuProductSeedDtos.java", JAVA_ROOT / "product/dto/MenuProductSeedDtos.java", "com.ael.algoryqrservice.product.dto"),
    (JAVA_ROOT / "menu/dto/ProductImageDtos.java", JAVA_ROOT / "product/dto/ProductImageDtos.java", "com.ael.algoryqrservice.product.dto"),
    (JAVA_ROOT / "menu/controller/MenuProductIndexController.java", JAVA_ROOT / "product/controller/MenuProductIndexController.java", "com.ael.algoryqrservice.product.controller"),
    (JAVA_ROOT / "menu/controller/ProductImageController.java", JAVA_ROOT / "product/controller/ProductImageController.java", "com.ael.algoryqrservice.product.controller"),
    (JAVA_ROOT / "menu/service/MenuProductOptionService.java", JAVA_ROOT / "product/service/MenuProductOptionService.java", "com.ael.algoryqrservice.product.service"),
    (JAVA_ROOT / "menu/service/MenuProductRatingService.java", JAVA_ROOT / "product/service/MenuProductRatingService.java", "com.ael.algoryqrservice.product.service"),
    (JAVA_ROOT / "menu/service/MenuProductPairingService.java", JAVA_ROOT / "product/service/MenuProductPairingService.java", "com.ael.algoryqrservice.product.service"),
    (JAVA_ROOT / "menu/service/MenuProductSeedService.java", JAVA_ROOT / "product/service/MenuProductSeedService.java", "com.ael.algoryqrservice.product.service"),
    (JAVA_ROOT / "menu/service/ProductImageStorageService.java", JAVA_ROOT / "product/service/ProductImageStorageService.java", "com.ael.algoryqrservice.product.service"),
]

MENUINDEX = JAVA_ROOT / "menu/service/menuindex"
PRODUCT_MENUINDEX = JAVA_ROOT / "product/service/menuindex"

REPLACEMENTS = [
    ("com.ael.algoryqrservice.qr.dto.ProductRequest", "com.ael.algoryqrservice.product.dto.ProductRequest"),
    ("com.ael.algoryqrservice.qr.dto.ProductResponse", "com.ael.algoryqrservice.product.dto.ProductResponse"),
    ("com.ael.algoryqrservice.menu.dto.MenuProductSeedDtos", "com.ael.algoryqrservice.product.dto.MenuProductSeedDtos"),
    ("com.ael.algoryqrservice.menu.dto.ProductImageDtos", "com.ael.algoryqrservice.product.dto.ProductImageDtos"),
    ("com.ael.algoryqrservice.menu.controller.MenuProductIndexController", "com.ael.algoryqrservice.product.controller.MenuProductIndexController"),
    ("com.ael.algoryqrservice.menu.controller.ProductImageController", "com.ael.algoryqrservice.product.controller.ProductImageController"),
    ("com.ael.algoryqrservice.menu.service.MenuProductOptionService", "com.ael.algoryqrservice.product.service.MenuProductOptionService"),
    ("com.ael.algoryqrservice.menu.service.MenuProductRatingService", "com.ael.algoryqrservice.product.service.MenuProductRatingService"),
    ("com.ael.algoryqrservice.menu.service.MenuProductPairingService", "com.ael.algoryqrservice.product.service.MenuProductPairingService"),
    ("com.ael.algoryqrservice.menu.service.MenuProductSeedService", "com.ael.algoryqrservice.product.service.MenuProductSeedService"),
    ("com.ael.algoryqrservice.menu.service.ProductImageStorageService", "com.ael.algoryqrservice.product.service.ProductImageStorageService"),
    ("com.ael.algoryqrservice.menu.service.menuindex", "com.ael.algoryqrservice.product.service.menuindex"),
]


def set_package(path: Path, package: str) -> None:
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"^package\s+[\w.]+;", f"package {package};", text, count=1, flags=re.MULTILINE)
    path.write_text(text, encoding="utf-8")


def main() -> None:
    for src, dest, pkg in MOVES:
        if not src.exists():
            print(f"skip missing {src}")
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(src), str(dest))
        set_package(dest, pkg)
        print(f"moved {src.name} -> product")

    if MENUINDEX.is_dir():
        PRODUCT_MENUINDEX.mkdir(parents=True, exist_ok=True)
        for src in MENUINDEX.glob("*.java"):
            dest = PRODUCT_MENUINDEX / src.name
            shutil.move(str(src), str(dest))
            set_package(dest, "com.ael.algoryqrservice.product.service.menuindex")
            print(f"moved menuindex/{src.name}")

    java_files = list(JAVA_ROOT.rglob("*.java"))
    for old, new in REPLACEMENTS:
        for path in java_files:
            text = path.read_text(encoding="utf-8")
            updated = text.replace(old, new)
            if updated != text:
                path.write_text(updated, encoding="utf-8")

    for path in java_files:
        text = path.read_text(encoding="utf-8")
        if "import com.ael.algoryqrservice.qr.dto.ProductResponse;" in text and "UberEats" in path.read_text(encoding="utf-8"):
            pass
    # Remove erroneous catalog ProductResponse imports from UberEats (unused)
    uber_paths = list((JAVA_ROOT / "integration/ubereats").rglob("*.java")) + [
        JAVA_ROOT / "integration/service/IntegrationExportService.java"
    ]
    for path in uber_paths:
        if not path.exists():
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        filtered = [ln for ln in lines if ln.strip() != "import com.ael.algoryqrservice.product.dto.ProductResponse;"
                    and ln.strip() != "import com.ael.algoryqrservice.qr.dto.ProductResponse;"]
        if len(filtered) != len(lines):
            path.write_text("\n".join(filtered) + "\n", encoding="utf-8")

    print("done")


if __name__ == "__main__":
    main()
