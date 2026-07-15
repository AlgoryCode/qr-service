package com.ael.algoryqrservice.model.enums;

public enum ProductScope {
    QR_CREATE_OWNER(ProductCode.QR_CREATE),
    QR_MENU_OWNER(ProductCode.QR_MENU);

    private final ProductCode productCode;

    ProductScope(ProductCode productCode) {
        this.productCode = productCode;
    }

    public ProductCode getProductCode() {
        return productCode;
    }
}
