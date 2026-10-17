#!/usr/bin/env python3
"""Replace old package references across src/main/java."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java"

REPLACEMENTS = [
    ("com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity",
     "com.ael.algoryqrservice.qrservice.merchant.infrastructure.persistence.entity.MerchantEntity"),
    ("com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository",
     "com.ael.algoryqrservice.qrservice.merchant.infrastructure.persistence.repository.MerchantJpaRepository"),
    ("com.ael.algoryqrservice.authservice.application.auth.FindMerchantUseCase",
     "com.ael.algoryqrservice.qrservice.merchant.application.FindMerchantUseCase"),
    ("com.ael.algoryqrservice.authservice.application.auth.SoftDeleteMerchantUseCase",
     "com.ael.algoryqrservice.qrservice.merchant.application.SoftDeleteMerchantUseCase"),
    ("com.ael.algoryqrservice.authservice.application.auth.UpsertStaffUseCase",
     "com.ael.algoryqrservice.qrservice.staff.application.UpsertStaffUseCase"),
    ("com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantStaffJpaRepository",
     "com.ael.algoryqrservice.qrservice.staff.infrastructure.persistence.repository.MerchantStaffRepository"),
    ("com.ael.algoryqrservice.qrservice.model.MerchantStaff",
     "com.ael.algoryqrservice.qrservice.staff.infrastructure.persistence.entity.MerchantStaff"),
    ("com.ael.algoryqrservice.qrservice.repository.MerchantStaffRepository",
     "com.ael.algoryqrservice.qrservice.staff.infrastructure.persistence.repository.MerchantStaffRepository"),
    ("com.ael.algoryqrservice.qrservice.waiter.service.WaiterAccessService",
     "com.ael.algoryqrservice.qrservice.staff.application.StaffAccessService"),
    ("com.ael.algoryqrservice.qrservice.waiter.service.",
     "com.ael.algoryqrservice.qrservice.staff.application."),
    ("com.ael.algoryqrservice.qrservice.waiter.controller.",
     "com.ael.algoryqrservice.qrservice.staff.presentation.controller."),
    ("com.ael.algoryqrservice.qrservice.waiter.dto.",
     "com.ael.algoryqrservice.qrservice.staff.dto."),
    ("com.ael.algoryqrservice.qrservice.merchant.service.MerchantCustomerService",
     "com.ael.algoryqrservice.qrservice.customer.application.MenuCustomerListService"),
    ("MerchantCustomerService", "MenuCustomerListService"),
    ('@RequestMapping("/waiter-panel")', '@RequestMapping("/staff/panel")'),
    ('@RequestMapping("/waiter/orders")', '@RequestMapping("/staff/orders")'),
    ('@RequestMapping("/waiter")', '@RequestMapping("/staff/ops")'),
    ('@RequestMapping("/waiter/kitchen")', '@RequestMapping("/staff/kitchen")'),
    ('@RequestMapping("/waiter/courier")', '@RequestMapping("/staff/courier")'),
    ('@RequestMapping("/waiter/commissions")', '@RequestMapping("/staff/commissions")'),
    ('@RequestMapping("/waiter/campaigns")', '@RequestMapping("/staff/campaigns")'),
    ("/internal/merchants", "/internal/merchant"),
]

SKIP_DIRS = {"qrservice/waiter", "qrservice/merchant/service"}


def should_skip(path: Path) -> bool:
    rel = path.relative_to(MAIN).as_posix()
    for s in SKIP_DIRS:
        if s in rel:
            return True
    return False


def main():
    count = 0
    for path in MAIN.rglob("*.java"):
        if should_skip(path):
            continue
        text = path.read_text(encoding="utf-8")
        original = text
        for old, new in REPLACEMENTS:
            text = text.replace(old, new)
        if text != original:
            path.write_text(text, encoding="utf-8")
            count += 1
    print(f"updated {count} files")


if __name__ == "__main__":
    main()
