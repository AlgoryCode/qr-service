#!/usr/bin/env python3
"""One-off: copy waiter package to staff with renames. Run from repo root."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
WAITER = ROOT / "src/main/java/com/ael/algoryqrservice/qrservice/waiter"
STAFF = ROOT / "src/main/java/com/ael/algoryqrservice/qrservice/staff"

REPLACEMENTS = [
    ("com.ael.algoryqrservice.qrservice.waiter.", "com.ael.algoryqrservice.qrservice.staff."),
    ("WaiterAccessService", "StaffAccessService"),
    ("WaiterCommissionService", "StaffCommissionService"),
    ("WaiterPanelController", "StaffPanelController"),
    ("WaiterKitchenController", "StaffKitchenController"),
    ("WaiterCourierController", "StaffCourierController"),
    ("WaiterCommissionController", "StaffCommissionController"),
    ("WaiterCampaignController", "StaffCampaignController"),
    ("WaiterOpsController", "StaffOpsController"),
    ('@RequestMapping("/waiter-panel")', '@RequestMapping("/staff/panel")'),
    ('@RequestMapping("/waiter/kitchen")', '@RequestMapping("/staff/kitchen")'),
    ('@RequestMapping("/waiter/courier")', '@RequestMapping("/staff/courier")'),
    ('@RequestMapping("/waiter/commissions")', '@RequestMapping("/staff/commissions")'),
    ('@RequestMapping("/waiter/campaigns")', '@RequestMapping("/staff/campaigns")'),
    ('@RequestMapping("/waiter/ops")', '@RequestMapping("/staff/ops")'),
    ("com.ael.algoryqrservice.qrservice.model.MerchantStaff",
     "com.ael.algoryqrservice.qrservice.staff.infrastructure.persistence.entity.MerchantStaff"),
    ("com.ael.algoryqrservice.qrservice.repository.MerchantStaffRepository",
     "com.ael.algoryqrservice.qrservice.staff.infrastructure.persistence.repository.MerchantStaffRepository"),
    ("com.ael.algoryqrservice.qrservice.merchant.service.MerchantCustomerService",
     "com.ael.algoryqrservice.qrservice.customer.application.MenuCustomerListService"),
    ("MerchantCustomerService", "MenuCustomerListService"),
]

PATH_MAP = {
    "controller": "presentation/controller",
    "service": "application",
    "dto": "dto",
}


def target_path(rel: Path) -> Path:
    parts = rel.parts
    kind = parts[0]
    rest = Path(*parts[1:])
    sub = PATH_MAP.get(kind, kind)
    return STAFF / sub / rest


def transform(content: str) -> str:
    for old, new in REPLACEMENTS:
        content = content.replace(old, new)
    return content


def main():
    if not WAITER.exists():
        print("waiter gone")
        return
    for src in WAITER.rglob("*.java"):
        rel = src.relative_to(WAITER)
        dst = target_path(rel)
        dst.parent.mkdir(parents=True, exist_ok=True)
        text = src.read_text(encoding="utf-8")
        dst.write_text(transform(text), encoding="utf-8")
        print("wrote", dst.relative_to(ROOT))


if __name__ == "__main__":
    main()
