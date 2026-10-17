#!/usr/bin/env python3
"""
Move each *Service.java and *Controller.java into a dedicated package named after the class:
  com.ael.algoryqrservice.{domain...}.{classname_lower}.service|controller
"""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java/com/ael/algoryqrservice"

PACKAGE_RE = re.compile(r"^package\s+([\w.]+);", re.MULTILINE)
SKIP_DIR_PARTS = {"service", "controller", "application", "presentation"}


def domain_prefix(parent: Path) -> str:
    parts = [p for p in parent.relative_to(JAVA_ROOT).parts if p not in SKIP_DIR_PARTS]
    return ".".join(parts)


def move_type(path: Path, layer: str, import_map: dict[str, str]) -> None:
    class_name = path.stem
    if layer == "service" and not class_name.endswith("Service"):
        return
    if layer == "controller" and not class_name.endswith("Controller"):
        return

    old_pkg_match = PACKAGE_RE.search(path.read_text(encoding="utf-8"))
    if not old_pkg_match:
        return
    old_pkg = old_pkg_match.group(1)
    old_fqcn = f"{old_pkg}.{class_name}"

    domain = domain_prefix(path.parent)
    feature = class_name.lower()
    if domain:
        new_pkg = f"com.ael.algoryqrservice.{domain}.{feature}.{layer}"
    else:
        new_pkg = f"com.ael.algoryqrservice.{feature}.{layer}"

    rel_pkg = new_pkg.removeprefix("com.ael.algoryqrservice.")
    dest = JAVA_ROOT.joinpath(*rel_pkg.split(".")) / f"{class_name}.java"
    dest.parent.mkdir(parents=True, exist_ok=True)

    text = path.read_text(encoding="utf-8")
    text = re.sub(r"^package\s+[\w.]+;", f"package {new_pkg};", text, count=1, flags=re.MULTILINE)
    dest.write_text(text, encoding="utf-8")
    if path.resolve() != dest.resolve():
        path.unlink()

    import_map[old_fqcn] = f"{new_pkg}.{class_name}"


def collect_files(suffix: str, layer: str) -> list[Path]:
    out: list[Path] = []
    for path in JAVA_ROOT.rglob(f"*{suffix}"):
        if "infrastructure" in path.parts and layer == "service":
            continue
        rel = path.relative_to(JAVA_ROOT)
        if layer == "service":
            if path.name.endswith("Service.java") and (
                "service" in rel.parts or "application" in rel.parts or rel.parent.name == "coupon"
            ):
                out.append(path)
        elif layer == "controller" and path.name.endswith("Controller.java"):
            if "controller" in rel.parts or "presentation" in rel.parts:
                out.append(path)
    return out


def apply_import_map(import_map: dict[str, str]) -> None:
    java_files = list(JAVA_ROOT.rglob("*.java"))
    for old, new in sorted(import_map.items(), key=lambda x: len(x[0]), reverse=True):
        for path in java_files:
            text = path.read_text(encoding="utf-8")
            updated = text.replace(old, new)
            if updated != text:
                path.write_text(updated, encoding="utf-8")


def prune_empty_dirs() -> None:
    for path in sorted(JAVA_ROOT.rglob("*"), reverse=True):
        if path.is_dir() and not any(path.rglob("*.java")):
            path.rmdir()


def main() -> None:
    import_map: dict[str, str] = {}

    # Controllers first (fewer), then services — paths change as we move
    for layer, suffix in [("controller", "Controller.java"), ("service", "Service.java")]:
        paths = collect_files(suffix, layer)
        # Stable order: deepest paths first to avoid moving into deleted parents
        paths.sort(key=lambda p: len(p.parts), reverse=True)
        for path in list(paths):
            if not path.exists():
                continue
            move_type(path, layer, import_map)

    apply_import_map(import_map)
    prune_empty_dirs()
    print(f"Repackaged {len(import_map)} service/controller types.")


if __name__ == "__main__":
    main()
