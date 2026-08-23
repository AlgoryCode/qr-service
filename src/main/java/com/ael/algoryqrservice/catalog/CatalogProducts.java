package com.ael.algoryqrservice.catalog;

public final class CatalogProducts {

    public static final String QR_CREATE = "QR_CREATE";
    public static final String QR_MENU = "QR_MENU";
    public static final String QR_BRANCH = "QR_BRANCH";
    public static final String MENU_PRODUCT = "MENU_PRODUCT";
    public static final String SMART_ASSISTANT = "SMART_ASSISTANT";
    public static final String SMART_SUMMARY = "SMART_SUMMARY";
    public static final String SMART_REPORTING = "SMART_REPORTING";
    public static final String CUSTOM_DESIGN = "CUSTOM_DESIGN";
    public static final String WAITER_PANEL = "WAITER_PANEL";

    public static boolean isAddonPurchasable(String code) {
        return QR_CREATE.equals(code) || QR_MENU.equals(code) || QR_BRANCH.equals(code) || MENU_PRODUCT.equals(code);
    }

    private CatalogProducts() {
    }
}
