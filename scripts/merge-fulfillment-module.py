import os
import re

src_root = None
desktop = r"C:\Users\Tarik\Desktop"
for name in os.listdir(desktop):
    cand = os.path.join(desktop, name, "qr-fulfillment-service", "src", "main", "java", "com", "qrfulfillmentservice")
    if os.path.isdir(cand):
        src_root = cand
        break
if src_root is None:
    raise SystemExit("qr-fulfillment-service source not found")

dest_root = os.path.join(
    os.path.dirname(os.path.dirname(__file__)),
    "src",
    "main",
    "java",
    "com",
    "ael",
    "algoryqrservice",
    "fulfillmentservice",
)

skip = {
    "FulfillmentServiceApplication.java",
    "HealthCheckController.java",
    "PaymentEventTypes.java",
}

renames = {
    "PaymentRabbitMqProperties": "FulfillmentPaymentRabbitMqProperties",
    "RabbitMqConfig": "FulfillmentModuleRabbitMqConfig",
    "PaymentEventConsumer": "FulfillmentModulePaymentEventConsumer",
    "GlobalExceptionHandler": "FulfillmentApiExceptionHandler",
}

pkg_old = "com.qrfulfillmentservice"
pkg_new = "com.ael.algoryqrservice.fulfillmentservice"

tx_manager = 'transactionManager = "fulfillmentTransactionManager"'


def patch_transactional(text: str) -> str:
    def repl(match: re.Match[str]) -> str:
        inner = match.group(1)
        if inner is None:
            return f"@Transactional({tx_manager})"
        if "transactionManager" in inner:
            return match.group(0)
        inner = inner.strip()
        if not inner:
            return f"@Transactional({tx_manager})"
        return f"@Transactional({inner}, {tx_manager})"

    return re.sub(r"@Transactional(\([^)]*\))?", repl, text)


count = 0
for root, _, files in os.walk(src_root):
    for fn in files:
        if not fn.endswith(".java") or fn in skip:
            continue
        src_path = os.path.join(root, fn)
        rel = os.path.relpath(src_path, src_root)
        dest_path = os.path.join(dest_root, rel)
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        text = open(src_path, encoding="utf-8").read()
        text = text.replace(pkg_old, pkg_new)
        for old, new in renames.items():
            text = text.replace(old, new)
        text = patch_transactional(text)
        open(dest_path, "w", encoding="utf-8").write(text)
        count += 1

print(f"copied {count} files from {src_root}")
