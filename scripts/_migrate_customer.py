#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java/com/ael/algoryqrservice/qrservice"

MOVES = [
    ("model/Customer.java", "customer/infrastructure/persistence/entity/Customer.java",
     "com.ael.algoryqrservice.qrservice.model", "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.entity"),
    ("model/CustomerMembership.java", "customer/infrastructure/persistence/entity/CustomerMembership.java",
     "com.ael.algoryqrservice.qrservice.model", "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.entity"),
    ("model/CustomerAddress.java", "customer/infrastructure/persistence/entity/CustomerAddress.java",
     "com.ael.algoryqrservice.qrservice.model", "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.entity"),
    ("repository/CustomerRepository.java", "customer/infrastructure/persistence/repository/CustomerRepository.java",
     "com.ael.algoryqrservice.qrservice.repository", "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.repository"),
    ("repository/CustomerMembershipRepository.java", "customer/infrastructure/persistence/repository/CustomerMembershipRepository.java",
     "com.ael.algoryqrservice.qrservice.repository", "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.repository"),
    ("repository/CustomerAddressRepository.java", "customer/infrastructure/persistence/repository/CustomerAddressRepository.java",
     "com.ael.algoryqrservice.qrservice.repository", "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.repository"),
    ("account/dto/CustomerAuthDtos.java", "customer/dto/CustomerAuthDtos.java",
     "com.ael.algoryqrservice.qrservice.account.dto", "com.ael.algoryqrservice.qrservice.customer.dto"),
    ("account/service/CustomerAccountService.java", "customer/application/CustomerAccountService.java",
     "com.ael.algoryqrservice.qrservice.account.service", "com.ael.algoryqrservice.qrservice.customer.application"),
    ("account/controller/CustomerAccountController.java", "customer/presentation/controller/CustomerAccountController.java",
     "com.ael.algoryqrservice.qrservice.account.controller", "com.ael.algoryqrservice.qrservice.customer.presentation.controller"),
    ("merchant/service/MerchantCustomerService.java", "customer/application/MenuCustomerListService.java",
     "com.ael.algoryqrservice.qrservice.merchant.service", "com.ael.algoryqrservice.qrservice.customer.application"),
    ("campaign/service/CustomerCampaignRewardService.java", "customer/application/CustomerCampaignRewardService.java",
     "com.ael.algoryqrservice.qrservice.campaign.service", "com.ael.algoryqrservice.qrservice.customer.application"),
]

CUSTOMER_REPOS = [
    ("com.ael.algoryqrservice.qrservice.model.Customer",
     "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.entity.Customer"),
    ("com.ael.algoryqrservice.qrservice.model.CustomerMembership",
     "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.entity.CustomerMembership"),
    ("com.ael.algoryqrservice.qrservice.model.CustomerAddress",
     "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.entity.CustomerAddress"),
    ("com.ael.algoryqrservice.qrservice.repository.CustomerRepository",
     "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.repository.CustomerRepository"),
    ("com.ael.algoryqrservice.qrservice.repository.CustomerMembershipRepository",
     "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.repository.CustomerMembershipRepository"),
    ("com.ael.algoryqrservice.qrservice.repository.CustomerAddressRepository",
     "com.ael.algoryqrservice.qrservice.customer.infrastructure.persistence.repository.CustomerAddressRepository"),
    ("com.ael.algoryqrservice.qrservice.account.dto.CustomerAuthDtos",
     "com.ael.algoryqrservice.qrservice.customer.dto.CustomerAuthDtos"),
    ("com.ael.algoryqrservice.qrservice.account.service.CustomerAccountService",
     "com.ael.algoryqrservice.qrservice.customer.application.CustomerAccountService"),
    ("com.ael.algoryqrservice.qrservice.account.controller.CustomerAccountController",
     "com.ael.algoryqrservice.qrservice.customer.presentation.controller.CustomerAccountController"),
    ("com.ael.algoryqrservice.qrservice.campaign.service.CustomerCampaignRewardService",
     "com.ael.algoryqrservice.qrservice.customer.application.CustomerCampaignRewardService"),
]


def main():
    for src_rel, dst_rel, old_pkg, new_pkg in MOVES:
        src = MAIN / src_rel.replace("/", "\\") if False else MAIN / Path(src_rel)
        dst = MAIN / Path(dst_rel)
        if not src.exists():
            print("skip missing", src)
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        text = src.read_text(encoding="utf-8").replace(old_pkg, new_pkg)
        if "MerchantCustomerService" in src.name:
            text = text.replace("MerchantCustomerService", "MenuCustomerListService")
            text = text.replace("com.ael.algoryqrservice.qrservice.merchant.service", "com.ael.algoryqrservice.qrservice.customer.application")
        if "CustomerAccountService" in dst.name:
            text = text.replace("com.ael.algoryqrservice.qrservice.account.dto", "com.ael.algoryqrservice.qrservice.customer.dto")
        if "CustomerAccountController" in dst.name:
            text = text.replace("com.ael.algoryqrservice.qrservice.account.dto", "com.ael.algoryqrservice.qrservice.customer.dto")
            text = text.replace("com.ael.algoryqrservice.qrservice.account.service", "com.ael.algoryqrservice.qrservice.customer.application")
            text = text.replace("com.ael.algoryqrservice.qrservice.campaign.service.CustomerCampaignRewardService",
                                "com.ael.algoryqrservice.qrservice.customer.application.CustomerCampaignRewardService")
        dst.write_text(text, encoding="utf-8")
        src.unlink()
        print("moved", src_rel, "->", dst_rel)

    count = 0
    for path in (ROOT / "src/main/java").rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        orig = text
        for old, new in CUSTOMER_REPOS:
            text = text.replace(old, new)
        if text != orig:
            path.write_text(text, encoding="utf-8")
            count += 1
    print("bulk customer imports", count)


if __name__ == "__main__":
    main()
