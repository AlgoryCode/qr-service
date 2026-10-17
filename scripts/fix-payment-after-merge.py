import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PS = ROOT / "src/main/java/com/ael/algoryqrservice/paymentservice"

for path in PS.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    text = text.replace("@Transactional((readOnly = true),", "@Transactional(readOnly = true,")
    path.write_text(text, encoding="utf-8")

for path in PS.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"import io\.swagger\.v3\.oas\.annotations\.[^\n]+\n", "", text)
    text = re.sub(r"@Tag\([^\)]*\)\n", "", text)
    text = re.sub(r"\s*@Operation\([^\)]*\)\n", "", text)
    text = re.sub(r"\s*@ApiResponse\([^\)]*\)\n", "", text)
    path.write_text(text, encoding="utf-8")

openapi = PS / "infrastructure/config/PaymentModuleOpenApiConfig.java"
if openapi.is_file():
    openapi.unlink()

print("payment cleanup done")
