package com.ael.algoryqrservice.demo;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;

import java.util.List;

public final class DemoAccess {

    private static final UserAccessProfile PROFILE = new UserAccessProfile(
            CatalogPackages.ULTIMATE_PACKAGE,
            List.of(
                    CatalogProducts.QR_CREATE,
                    CatalogProducts.QR_MENU,
                    CatalogProducts.QR_BRANCH,
                    CatalogProducts.MENU_PRODUCT,
                    CatalogProducts.WAITER_PANEL,
                    CatalogProducts.SMART_ASSISTANT,
                    CatalogProducts.SMART_SUMMARY,
                    CatalogProducts.SMART_REPORTING,
                    CatalogProducts.CUSTOM_DESIGN,
                    CatalogProducts.AI_MENU_IMPORT,
                    CatalogProducts.ONLINE_ORDER
            ),
            List.of(
                    CatalogScopes.QR_CREATE_OWNER,
                    CatalogScopes.QR_MENU_OWNER,
                    CatalogScopes.QR_BRANCH_OWNER,
                    CatalogScopes.MENU_PRODUCT_OWNER,
                    CatalogScopes.WAITER_PANEL_OWNER,
                    CatalogScopes.SMART_ASSISTANT_OWNER,
                    CatalogScopes.SMART_SUMMARY_OWNER,
                    CatalogScopes.SMART_REPORTING_OWNER,
                    CatalogScopes.CUSTOM_DESIGN_OWNER,
                    CatalogScopes.AI_MENU_IMPORT_OWNER,
                    CatalogScopes.ONLINE_ORDER_OWNER
            )
    );

    private DemoAccess() {
    }

    public static UserAccessProfile profile() {
        return PROFILE;
    }
}
