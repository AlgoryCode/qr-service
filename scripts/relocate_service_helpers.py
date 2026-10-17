#!/usr/bin/env python3
"""Move leftover classes under */service/ next to the best-matching *Service package."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java/com/ael/algoryqrservice"

PACKAGE_RE = re.compile(r"^package\s+([\w.]+);", re.MULTILINE)


def service_packages() -> dict[str, str]:
    """Map lowercase stem prefix -> package (without class name)."""
    mapping: dict[str, str] = {}
    for path in JAVA_ROOT.rglob("*Service.java"):
        if path.parent.name != "service" or path.parent.parent.name.endswith("service"):
            pkg_match = PACKAGE_RE.search(path.read_text(encoding="utf-8"))
            if not pkg_match:
                continue
            pkg = pkg_match.group(1)
            stem = path.stem.removesuffix("Service").lower()
            mapping[stem] = pkg
    return mapping


def target_package(class_name: str, svc_map: dict[str, str]) -> str | None:
    base = class_name
    for suffix in ("Assembler", "Helper", "Gateway", "Mapper", "Support", "Policy", "Guard", "Factory", "Builder", "Notifier", "Generator", "Aggregates", "Pricing", "Quantities"):
        if base.endswith(suffix):
            base = base[: -len(suffix)]
            break
    key = base.lower()
    if key in svc_map:
        return svc_map[key]
    # longest prefix match
    best = ""
    best_pkg = None
    for stem, pkg in svc_map.items():
        if key.startswith(stem) and len(stem) > len(best):
            best = stem
            best_pkg = pkg
    return best_pkg


def main() -> None:
    svc_map = service_packages()
    import_map: dict[str, str] = {}

    legacy = []
    for path in JAVA_ROOT.rglob("*.java"):
        rel = path.relative_to(JAVA_ROOT)
        if "service" not in rel.parts:
            continue
        if path.parent.name == "service" and not path.parent.parent.name.endswith("service"):
            legacy.append(path)
        # billing/service/foo (not moved)
        if path.parent.name == "service" and len(rel.parts) >= 3 and not rel.parts[-3].endswith("service"):
            if path not in legacy:
                legacy.append(path)

    for path in legacy:
        class_name = path.stem
        if class_name.endswith("Service"):
            continue
        new_pkg = target_package(class_name, svc_map)
        if not new_pkg:
            continue
        old_pkg_match = PACKAGE_RE.search(path.read_text(encoding="utf-8"))
        if not old_pkg_match:
            continue
        old_pkg = old_pkg_match.group(1)
        old_fqcn = f"{old_pkg}.{class_name}"

        rel_pkg = new_pkg.removeprefix("com.ael.algoryqrservice.")
        dest = JAVA_ROOT.joinpath(*rel_pkg.split(".")) / f"{class_name}.java"
        if dest.resolve() == path.resolve():
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        text = path.read_text(encoding="utf-8")
        text = re.sub(r"^package\s+[\w.]+;", f"package {new_pkg};", text, count=1, flags=re.MULTILINE)
        dest.write_text(text, encoding="utf-8")
        path.unlink()
        import_map[old_fqcn] = f"{new_pkg}.{class_name}"

    java_files = list(JAVA_ROOT.rglob("*.java"))
    for old, new in sorted(import_map.items(), key=lambda x: len(x[0]), reverse=True):
        for jpath in java_files:
            text = jpath.read_text(encoding="utf-8")
            updated = text.replace(old, new)
            if updated != text:
                jpath.write_text(updated, encoding="utf-8")

    print(f"Relocated {len(import_map)} helper types.")


if __name__ == "__main__":
    main()
