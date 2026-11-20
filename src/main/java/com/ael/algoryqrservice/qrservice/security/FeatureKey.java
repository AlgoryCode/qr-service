package com.ael.algoryqrservice.qrservice.security;

/**
 * Every feature the code checks or meters, by its {@code features.feature_key}. Referring to a
 * feature through this enum makes a typo a compile error; {@link FeatureCatalogValidator} checks at
 * startup that each one exists in the catalog.
 */
public enum FeatureKey {
    QR_BRANCH,
    QR_MENU,
    MENU_PRODUCT,
    QR_CREATE,
    SMART_REPORTING,
    SMART_REPORT_QUOTA,
    SMART_ASSISTANT,
    ASSISTANT_QUESTION,
    SMART_SUMMARY,
    CUSTOM_DESIGN,
    WAITER_PANEL,
    AI_MENU_IMPORT,
    AI_MENU_IMPORT_RUN,
    ONLINE_ORDER,
    KAFE_MENU,
    CONSOLE_RENTAL
}
