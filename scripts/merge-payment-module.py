import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
desktop = Path.home() / "Desktop"
src_root = None
for child in desktop.iterdir():
    cand = child / "payment-service" / "src" / "main" / "java" / "com" / "paymentservice"
    if cand.is_dir():
        src_root = cand
        break
if src_root is None:
    raise SystemExit("payment-service source not found under Desktop")

dest_root = ROOT / "src/main/java/com/ael/algoryqrservice/paymentservice"

skip = {
    "PaymentServiceApplication.java",
}

renames = {
    "GlobalExceptionHandler": "PaymentApiExceptionHandler",
    "RabbitMqMessagingConfig": "PaymentModuleRabbitMqConfig",
    "OpenApiConfig": "PaymentModuleOpenApiConfig",
    "ServiceAuthenticationFilter": "PaymentServiceAuthenticationFilter",
    "PaymentEventsRabbitMqConfig": "PaymentModuleEventsRabbitMqConfig",
    "PushNotificationRabbitMqConfig": "PaymentModulePushNotificationRabbitMqConfig",
}

pkg_old = "com.paymentservice"
pkg_new = "com.ael.algoryqrservice.paymentservice"

tx_manager = 'transactionManager = "paymentTransactionManager"'


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
        src_path = Path(root) / fn
        rel = os.path.relpath(src_path, src_root)
        dest_path = dest_root / rel
        dest_path.parent.mkdir(parents=True, exist_ok=True)
        text = src_path.read_text(encoding="utf-8")
        text = text.replace(pkg_old, pkg_new)
        for old, new in renames.items():
            if old in fn:
                dest_path = dest_path.with_name(fn.replace(old, new))
            text = text.replace(old, new)
        text = patch_transactional(text)
        dest_path.write_text(text, encoding="utf-8")
        count += 1

print(f"copied {count} files from {src_root}")
