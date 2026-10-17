#!/usr/bin/env python3
"""Add missing imports after domain repackage."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java/com/ael/algoryqrservice"

PACKAGE_RE = re.compile(r"^package\s+([\w.]+);", re.MULTILINE)
IMPORT_RE = re.compile(r"^import\s+([\w.]+);", re.MULTILINE)
TYPE_RE = re.compile(r"\b([A-Z][A-Za-z0-9_]*)\b")

SKIP_TYPES = {
    "String", "Long", "Integer", "Boolean", "BigDecimal", "List", "Map", "Set",
    "Optional", "Void", "Object", "Class", "Enum", "Record", "Override", "Deprecated",
    "SuppressWarnings", "Nullable", "NotNull", "Valid", "PathVariable", "RequestBody",
    "RequestParam", "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping",
    "RestController", "RequestMapping", "Service", "Component", "Repository", "Entity",
    "Transactional", "Builder", "Data", "Slf4j", "RequiredArgsConstructor", "AllArgsConstructor",
    "NoArgsConstructor", "ResponseEntity", "HttpStatus", "MultipartFile", "LocalDateTime",
    "LocalDate", "UUID", "Instant", "Duration", "Page", "Pageable", "Sort", "Specification",
    "Collectors", "Stream", "Arrays", "Objects", "Math", "Exception", "RuntimeException",
    "IOException", "Json", "JsonProperty", "JsonIgnore", "JsonInclude", "Schema",
}


def build_registry() -> dict[str, str]:
    registry: dict[str, str] = {}
    for path in JAVA_ROOT.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        m = PACKAGE_RE.search(text)
        if not m:
            continue
        pkg = m.group(1)
        registry[path.stem] = f"{pkg}.{path.stem}"
    return registry


def fix_file(path: Path, registry: dict[str, str]) -> bool:
    text = path.read_text(encoding="utf-8")
    pkg_m = PACKAGE_RE.search(text)
    if not pkg_m:
        return False
    current_pkg = pkg_m.group(1)
    existing = {imp.rsplit(".", 1)[-1] for imp in IMPORT_RE.findall(text)}
    static_imports = set(re.findall(r"^import static [\w.]+;", text, re.MULTILINE))

    candidates: set[str] = set()
    for match in TYPE_RE.finditer(text):
        name = match.group(1)
        if name in SKIP_TYPES or name in existing:
            continue
        fq = registry.get(name)
        if not fq:
            continue
        fq_pkg = fq.rsplit(".", 1)[0]
        if fq_pkg == current_pkg:
            continue
        candidates.add(fq)

    if not candidates:
        return False

    imports_block = "\n".join(f"import {fq};" for fq in sorted(candidates))
    if "import " in text:
        # insert after last import
        last_import = max(IMPORT_RE.finditer(text), key=lambda m: m.start())
        insert_at = last_import.end()
        new_text = text[:insert_at] + "\n" + imports_block + text[insert_at:]
    else:
        insert_at = pkg_m.end()
        new_text = text[:insert_at] + "\n\n" + imports_block + text[insert_at:]

    path.write_text(new_text, encoding="utf-8")
    return True


def main() -> None:
    registry = build_registry()
    changed = 0
    for path in JAVA_ROOT.rglob("*.java"):
        if fix_file(path, registry):
            changed += 1
    print(f"Updated imports in {changed} files.")


if __name__ == "__main__":
    main()
