#!/usr/bin/env python3
"""Move merchant/staff/customer from qrservice.* to top-level *service packages."""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "src/main/java/com/ael/algoryqrservice"

MOVES = [
    ("qrservice/merchant", "merchantservice"),
    ("qrservice/staff", "staffservice"),
    ("qrservice/customer", "customerservice"),
]

REPLACEMENTS = [
    ("com.ael.algoryqrservice.qrservice.merchant.", "com.ael.algoryqrservice.merchantservice."),
    ("com.ael.algoryqrservice.qrservice.staff.", "com.ael.algoryqrservice.staffservice."),
    ("com.ael.algoryqrservice.qrservice.customer.", "com.ael.algoryqrservice.customerservice."),
]


def move_trees():
    for src_rel, dst_rel in MOVES:
        src = BASE / src_rel
        dst = BASE / dst_rel
        if not src.exists():
            print("skip missing", src)
            continue
        if dst.exists():
            shutil.rmtree(dst)
        shutil.copytree(src, dst)
        shutil.rmtree(src)
        print("moved tree", src_rel, "->", dst_rel)


def rewrite_all_java():
    count = 0
    for path in (ROOT / "src").rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        orig = text
        for old, new in REPLACEMENTS:
            text = text.replace(old, new)
        if text != orig:
            path.write_text(text, encoding="utf-8")
            count += 1
    print("rewrote", count, "java files")


def rewrite_docs():
    for path in (ROOT / "docs").rglob("*.md"):
        text = path.read_text(encoding="utf-8")
        orig = text
        for old, new in REPLACEMENTS:
            text = text.replace(old, new)
        if text != orig:
            path.write_text(text, encoding="utf-8")
            print("doc", path.name)


if __name__ == "__main__":
    move_trees()
    rewrite_all_java()
    rewrite_docs()
