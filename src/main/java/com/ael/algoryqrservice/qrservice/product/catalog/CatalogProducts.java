package com.ael.algoryqrservice.qrservice.product.catalog;

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
    public static final String AI_MENU_IMPORT = "AI_MENU_IMPORT";
    public static final String ONLINE_ORDER = "ONLINE_ORDER";

    public static final String QR_MENU_ADDON = "QR_MENU_ADDON";
    public static final String QR_BRANCH_ADDON = "QR_BRANCH_ADDON";
    public static final String SMART_REPORTING_ADDON = "SMART_REPORTING_ADDON";
    /** Free smart reports per quota period granted by a package (its line quantity). */
    public static final String SMART_REPORT_QUOTA = "SMART_REPORT_QUOTA";

    private CatalogProducts() {
    }

    /**
     * Products whose counter tracks things that still exist (branches, menus, QR codes, menu
     * products). Their used count is carried over when the package renews or changes, and is given
     * back when the thing is deleted. Every other countable product is a per-period allowance.
     */
    public static final java.util.Set<String> COUNTED_RESOURCES = java.util.Set.of(
            QR_CREATE, QR_MENU, QR_BRANCH, MENU_PRODUCT);
}
