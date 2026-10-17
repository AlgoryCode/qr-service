#!/usr/bin/env python3
"""Remove imports that clash with a type declared in the same compilation unit."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java"

TYPE_DECL = re.compile(r"^public\s+(?:final\s+|abstract\s+)?(class|enum|record|interface)\s+(\w+)", re.MULTILINE)
IMPORT = re.compile(r"^import\s+([\w.]+);\s*$")


def main() -> None:
    removed = 0
    for path in JAVA_ROOT.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        declared = {m.group(2) for m in TYPE_DECL.finditer(text)}
        if not declared:
            continue
        lines = text.splitlines()
        new_lines = []
        changed = False
        for line in lines:
            m = IMPORT.match(line)
            if m and m.group(1).rsplit(".", 1)[-1] in declared:
                changed = True
                removed += 1
                continue
            new_lines.append(line)
        if changed:
            path.write_text("\n".join(new_lines) + ("\n" if text.endswith("\n") else ""), encoding="utf-8")
    print(f"removed {removed} conflicting imports")


if __name__ == "__main__":
    main()
