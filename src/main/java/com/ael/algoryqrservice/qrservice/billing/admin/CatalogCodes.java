package com.ael.algoryqrservice.qrservice.billing.admin;

import com.ael.algoryqrservice.qrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;

import java.util.List;

/**
 * The rights and panel scopes the application actually checks. A product carrying any other code
 * would sell something nothing enforces, so the admin catalog only accepts codes from these lists.
 */
public final class CatalogCodes {

    public record Option(String code, String label) {
    }

    public record Response(List<Option> features, List<Option> scopes) {
    }

    public static final List<Option> FEATURES = List.of(
            new Option(CatalogProducts.QR_CREATE, "QR kod oluşturma"),
            new Option(CatalogProducts.QR_MENU, "QR menü"),
            new Option(CatalogProducts.QR_BRANCH, "Şube"),
            new Option(CatalogProducts.MENU_PRODUCT, "Menü ürünü"),
            new Option(CatalogProducts.SMART_ASSISTANT, "Akıllı asistan"),
            new Option(CatalogProducts.SMART_SUMMARY, "Akıllı özet"),
            new Option(CatalogProducts.SMART_REPORTING, "Akıllı raporlama"),
            new Option(CatalogProducts.SMART_REPORT_QUOTA, "Akıllı rapor kotası"),
            new Option(CatalogProducts.CUSTOM_DESIGN, "Özel tasarım"),
            new Option(CatalogProducts.WAITER_PANEL, "Garson paneli"),
            new Option(CatalogProducts.AI_MENU_IMPORT, "Yapay zekâ ile menü aktarma"),
            new Option(CatalogProducts.ONLINE_ORDER, "Online sipariş"),
            new Option(CatalogProducts.CONSOLE_RENTAL, "Konsol takip")
    );

    public static final List<Option> SCOPES = List.of(
            new Option(CatalogScopes.QR_CREATE_OWNER, "QR kod oluşturma"),
            new Option(CatalogScopes.QR_MENU_OWNER, "QR menü"),
            new Option(CatalogScopes.QR_BRANCH_OWNER, "Şube"),
            new Option(CatalogScopes.MENU_PRODUCT_OWNER, "Menü ürünü"),
            new Option(CatalogScopes.SMART_ASSISTANT_OWNER, "Akıllı asistan"),
            new Option(CatalogScopes.SMART_SUMMARY_OWNER, "Akıllı özet"),
            new Option(CatalogScopes.SMART_REPORTING_OWNER, "Akıllı raporlama"),
            new Option(CatalogScopes.CUSTOM_DESIGN_OWNER, "Özel tasarım"),
            new Option(CatalogScopes.WAITER_PANEL_OWNER, "Garson paneli"),
            new Option(CatalogScopes.AI_MENU_IMPORT_OWNER, "Yapay zekâ ile menü aktarma"),
            new Option(CatalogScopes.ONLINE_ORDER_OWNER, "Online sipariş"),
            new Option(CatalogScopes.CONSOLE_RENTAL_OWNER, "Konsol takip")
    );

    public static final Response ALL = new Response(FEATURES, SCOPES);

    private CatalogCodes() {
    }

    public static boolean isFeature(String code) {
        return FEATURES.stream().anyMatch(option -> option.code().equals(code));
    }

    public static boolean isScope(String code) {
        return SCOPES.stream().anyMatch(option -> option.code().equals(code));
    }
}
