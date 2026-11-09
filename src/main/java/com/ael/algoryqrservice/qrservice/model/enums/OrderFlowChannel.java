package com.ael.algoryqrservice.qrservice.model.enums;

/** Siparişin geldiği yer; her kanalın kendi adım listesi vardır. */
public enum OrderFlowChannel {
    /** Garsonun masaya girdiği veya müşterinin masadaki QR'dan verdiği sipariş. */
    TABLE("Masa"),
    /** Online mağaza, platform veya telefonla gelen paket siparişi. */
    DELIVERY("Paket");

    private final String label;

    OrderFlowChannel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
