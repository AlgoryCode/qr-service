import re
from pathlib import Path

migration_dir = Path(__file__).resolve().parents[1] / "src/main/resources/db/migration"
java_dir = Path(__file__).resolve().parents[1] / "src/main/java"

tables: set[str] = set()
for f in sorted(migration_dir.glob("*.sql")):
    text = f.read_text(encoding="utf-8", errors="ignore")
    for m in re.finditer(r"CREATE TABLE(?: IF NOT EXISTS)?\s+([a-zA-Z0-9_\"]+)", text, re.I):
        tables.add(m.group(1).strip('"').lower())
    for m in re.finditer(r"DROP TABLE(?: IF EXISTS)?\s+([a-zA-Z0-9_\"]+)", text, re.I):
        tables.discard(m.group(1).strip('"').lower())

jtables: set[str] = set()
for f in java_dir.rglob("*.java"):
    text = f.read_text(encoding="utf-8", errors="ignore")
    for m in re.finditer(r'@Table\(name\s*=\s*"([^"]+)"', text):
        jtables.add(m.group(1).lower())

orphan = sorted(tables - jtables)
print(f"Tables in migrations not mapped by JPA ({len(orphan)}):")
for name in orphan:
    print(name)
