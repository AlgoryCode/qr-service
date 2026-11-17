package com.ael.algoryqrservice.qrservice.model.enums;

public enum MenuAnalyticsEventType {
    MENU_OPEN,
    CATEGORY_VIEW,
    PRODUCT_VIEW,
    SERVES_FILTER,
    QR_SCAN,
    ADD_TO_CART,
    REMOVE_FROM_CART,
    CHECKOUT_START,
    ORDER_SUBMITTED,
    // Menu assistant ("Akıllı Şef") funnel, sent by the guest menu.
    CHEF_CHAT_OPEN,
    CHEF_RECOMMENDATION_REQUEST,
    CHEF_RECOMMENDATION_SHOWN,
    CHEF_PRODUCT_CARD_CLICK,
    CHEF_PRODUCT_ADD,
    CHEF_CROSS_SELL_SHOWN,
    CHEF_CROSS_SELL_CLICK,
    CHEF_CROSS_SELL_ADD,
    CHEF_CART_OPEN,
    CHEF_RECOMMENDATION_FEEDBACK
}
