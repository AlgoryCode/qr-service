import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FF = ROOT / "src/main/java/com/ael/algoryqrservice/fulfillmentservice"

for path in FF.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    text = text.replace("@Transactional((readOnly = true),", "@Transactional(readOnly = true,")
    text = text.replace(
        "com.ael.algoryqrservice.fulfillmentservice.infrastructure.messaging.PaymentEventTypes",
        "com.ael.algoryqrservice.qrservice.messaging.payment.PaymentEventTypes",
    )
    path.write_text(text, encoding="utf-8")

ctrl = FF / "presentation/controller/FulfillmentController.java"
if ctrl.is_file():
    text = ctrl.read_text(encoding="utf-8")
    text = re.sub(r"import io\.swagger\.v3\.oas\.annotations\.[^\n]+\n", "", text)
    text = re.sub(r"@Tag\([^\)]*\)\n", "", text)
    text = re.sub(r"\s*@Operation\([^\)]*\)\n", "", text)
    ctrl.write_text(text, encoding="utf-8")

print("fulfillment cleanup done")
