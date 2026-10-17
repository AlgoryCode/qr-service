"""Remove duplicate entity packages and dead fulfillment copies."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src" / "main" / "java"
TEXT = {".java"}

# product.model is canonical for menu catalog entities; model for billing/catalog Product.
DELETE_FILES = [
    JAVA / "com/ael/algoryqrservice/qrservice/product/model/Product.java",
    JAVA / "com/ael/algoryqrservice/qrservice/product/model/enums/ProductBillingType.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/MenuProduct.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/MenuProductOption.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/MenuProductOptionGroup.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/MenuProductPairing.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/MenuProductRating.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/MenuProductVisit.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/SelectedMenuOption.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/IntegrationPendingProduct.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/enums/MenuProductOptionGroupKind.java",
    JAVA / "com/ael/algoryqrservice/qrservice/model/enums/MenuProductOptionUnit.java",
]

DELETE_DIRS = [
    JAVA / "com/ael/algoryqrservice/fulfillmentservice/service",
    JAVA / "com/ael/algoryqrservice/fulfillmentservice/controller",
    JAVA / "com/ael/algoryqrservice/fulfillmentservice/messaging",
]

REPLACEMENTS = [
    (
        "com.ael.algoryqrservice.qrservice.product.model.enums.ProductBillingType",
        "com.ael.algoryqrservice.qrservice.model.enums.ProductBillingType",
    ),
    (
        "com.ael.algoryqrservice.qrservice.product.model.Product",
        "com.ael.algoryqrservice.qrservice.model.Product",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.MenuProduct",
        "com.ael.algoryqrservice.qrservice.product.model.MenuProduct",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.MenuProductOption",
        "com.ael.algoryqrservice.qrservice.product.model.MenuProductOption",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.MenuProductOptionGroup",
        "com.ael.algoryqrservice.qrservice.product.model.MenuProductOptionGroup",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.MenuProductPairing",
        "com.ael.algoryqrservice.qrservice.product.model.MenuProductPairing",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.MenuProductRating",
        "com.ael.algoryqrservice.qrservice.product.model.MenuProductRating",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.MenuProductVisit",
        "com.ael.algoryqrservice.qrservice.product.model.MenuProductVisit",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.SelectedMenuOption",
        "com.ael.algoryqrservice.qrservice.product.model.SelectedMenuOption",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.IntegrationPendingProduct",
        "com.ael.algoryqrservice.qrservice.product.model.IntegrationPendingProduct",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.enums.MenuProductOptionGroupKind",
        "com.ael.algoryqrservice.qrservice.product.model.enums.MenuProductOptionGroupKind",
    ),
    (
        "com.ael.algoryqrservice.qrservice.model.enums.MenuProductOptionUnit",
        "com.ael.algoryqrservice.qrservice.product.model.enums.MenuProductOptionUnit",
    ),
]


def main() -> None:
    import shutil

    for path in DELETE_FILES:
        if path.is_file():
            path.unlink()
            print("deleted", path.relative_to(ROOT))

    for path in DELETE_DIRS:
        if path.is_dir():
            shutil.rmtree(path)
            print("removed dir", path.relative_to(ROOT))

    updated = 0
    for path in (ROOT / "src").rglob("*"):
        if not path.is_file() or path.suffix not in TEXT:
            continue
        text = path.read_text(encoding="utf-8")
        original = text
        for old, new in REPLACEMENTS:
            text = text.replace(old, new)
        if text != original:
            path.write_text(text, encoding="utf-8")
            updated += 1
    print(f"updated {updated} files")


if __name__ == "__main__":
    main()
