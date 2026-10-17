#!/usr/bin/env python3
"""
Flatten per-class *controller/controller and *service/service nests into domain modules:
  com.ael.algoryqrservice.<domain>[.<sub>].controller|service|dto|model|repository

Also moves domain-owned JPA types out of global model/ and repository/.
"""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java/com/ael/algoryqrservice"
BASE = "com.ael.algoryqrservice"

PACKAGE_RE = re.compile(r"^package\s+([\w.]+);", re.MULTILINE)

# --- entity / repo relocation (global -> domain module) ---

PRODUCT_MODELS = [
    "Product",
    "MenuProduct",
    "MenuProductOption",
    "MenuProductOptionGroup",
    "MenuProductPairing",
    "MenuProductRating",
    "MenuProductVisit",
    "IntegrationPendingProduct",
    "SelectedMenuOption",
]

PRODUCT_ENUMS = [
    "ProductBillingType",
    "MenuProductOptionGroupKind",
    "MenuProductOptionUnit",
]

PRODUCT_REPOS = [
    "ProductRepository",
    "MenuProductRepository",
    "MenuProductOptionRepository",
    "MenuProductOptionGroupRepository",
    "MenuProductPairingRepository",
    "MenuProductRatingRepository",
    "MenuProductVisitRepository",
    "IntegrationPendingProductRepository",
    "MenuProductSpecifications",
]

QR_MODELS = ["Qr", "QrBaseModel", "QrType"]
QR_REPOS = ["QrRepository"]

OTHER_FILE_MOVES: list[tuple[Path, Path, str]] = [
    (
        JAVA_ROOT / "catalog/CatalogProducts.java",
        JAVA_ROOT / "product/catalog/CatalogProducts.java",
        f"{BASE}.product.catalog",
    ),
    (
        JAVA_ROOT / "messaging/dto/MenuProductIndexMessage.java",
        JAVA_ROOT / "product/dto/MenuProductIndexMessage.java",
        f"{BASE}.product.dto",
    ),
    (
        JAVA_ROOT / "messaging/fulfillment/FulfillmentQueueMessage.java",
        JAVA_ROOT / "fulfillment/messaging/FulfillmentQueueMessage.java",
        f"{BASE}.fulfillment.messaging",
    ),
    (
        JAVA_ROOT / "messaging/fulfillment/FulfillmentQueuePublisher.java",
        JAVA_ROOT / "fulfillment/messaging/FulfillmentQueuePublisher.java",
        f"{BASE}.fulfillment.messaging",
    ),
]


def fqcn_from_path(path: Path) -> tuple[str, str]:
    text = path.read_text(encoding="utf-8")
    m = PACKAGE_RE.search(text)
    if not m:
        raise ValueError(f"No package in {path}")
    pkg = m.group(1)
    return pkg, f"{pkg}.{path.stem}"


def set_package(path: Path, package: str) -> None:
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"^package\s+[\w.]+;", f"package {package};", text, count=1, flags=re.MULTILINE)
    path.write_text(text, encoding="utf-8")


def subdomain_mid(parts: tuple[str, ...]) -> tuple[str, ...]:
    mid = list(parts[1:-3])
    while mid and mid[0] == parts[0]:
        mid.pop(0)
    return tuple(mid)


def flatten_layer_paths(layer: str) -> list[tuple[Path, Path, str]]:
    """Returns (src, dest, new_package) for files to flatten."""
    moves: list[tuple[Path, Path, str]] = []
    suffix = f"{layer}/{layer}"
    for path in JAVA_ROOT.rglob("*.java"):
        rel = path.relative_to(JAVA_ROOT)
        parts = rel.parts
        if len(parts) < 4:
            continue
        if parts[-2] != layer:
            continue
        feature = parts[-3]
        if layer == "controller" and not feature.endswith("controller"):
            continue
        if layer == "service" and not feature.endswith("service"):
            continue

        domain = parts[0]
        mid = subdomain_mid(parts)
        if layer == "controller":
            if mid:
                dest_rel = (domain, *mid, "controller", parts[-1])
            else:
                dest_rel = (domain, "controller", parts[-1])
        else:
            if mid:
                dest_rel = (domain, "service", *mid, parts[-1])
            else:
                dest_rel = (domain, "service", parts[-1])

        dest = JAVA_ROOT.joinpath(*dest_rel)
        if dest.resolve() == path.resolve():
            continue
        pkg_suffix = ".".join(dest_rel[:-1])
        new_pkg = f"{BASE}.{pkg_suffix}"
        moves.append((path, dest, new_pkg))
    return moves


def move_type(src: Path, dest: Path, new_pkg: str, import_map: dict[str, str]) -> None:
    if not src.exists():
        return
    old_fqcn = fqcn_from_path(src)[1]
    dest.parent.mkdir(parents=True, exist_ok=True)
    text = src.read_text(encoding="utf-8")
    text = re.sub(r"^package\s+[\w.]+;", f"package {new_pkg};", text, count=1, flags=re.MULTILINE)
    dest.write_text(text, encoding="utf-8")
    new_fqcn = f"{new_pkg}.{src.stem}"
    import_map[old_fqcn] = new_fqcn
    if src.resolve() != dest.resolve():
        src.unlink()


def apply_import_map(import_map: dict[str, str]) -> None:
    java_files = list(JAVA_ROOT.rglob("*.java"))
    for old, new in sorted(import_map.items(), key=lambda x: len(x[0]), reverse=True):
        for path in java_files:
            text = path.read_text(encoding="utf-8")
            updated = text.replace(old, new)
            if updated != text:
                path.write_text(updated, encoding="utf-8")


def cleanup_empty_dirs() -> None:
    for path in sorted(JAVA_ROOT.rglob("*"), key=lambda p: len(p.parts), reverse=True):
        if path.is_dir() and not any(path.rglob("*.java")):
            shutil.rmtree(path, ignore_errors=True)


def main() -> None:
    import_map: dict[str, str] = {}

    for layer in ("controller", "service"):
        for src, dest, new_pkg in flatten_layer_paths(layer):
            move_type(src, dest, new_pkg, import_map)

    for name in PRODUCT_MODELS:
        src = JAVA_ROOT / "model" / f"{name}.java"
        dest = JAVA_ROOT / "product/model" / f"{name}.java"
        move_type(src, dest, f"{BASE}.product.model", import_map)

    for name in PRODUCT_ENUMS:
        src = JAVA_ROOT / "model/enums" / f"{name}.java"
        dest = JAVA_ROOT / "product/model/enums" / f"{name}.java"
        move_type(src, dest, f"{BASE}.product.model.enums", import_map)

    for name in PRODUCT_REPOS:
        src = JAVA_ROOT / "repository" / f"{name}.java"
        dest = JAVA_ROOT / "product/repository" / f"{name}.java"
        move_type(src, dest, f"{BASE}.product.repository", import_map)

    for name in QR_MODELS:
        src = JAVA_ROOT / "model" / f"{name}.java"
        dest = JAVA_ROOT / "qr/model" / f"{name}.java"
        move_type(src, dest, f"{BASE}.qr.model", import_map)

    for name in QR_REPOS:
        src = JAVA_ROOT / "repository" / f"{name}.java"
        dest = JAVA_ROOT / "qr/repository" / f"{name}.java"
        move_type(src, dest, f"{BASE}.qr.repository", import_map)

    for src, dest, pkg in OTHER_FILE_MOVES:
        move_type(src, dest, pkg, import_map)

    apply_import_map(import_map)
    cleanup_empty_dirs()
    print(f"consolidated {len(import_map)} types")


if __name__ == "__main__":
    main()
