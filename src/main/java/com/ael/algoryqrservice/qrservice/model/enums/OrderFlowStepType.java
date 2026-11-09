package com.ael.algoryqrservice.qrservice.model.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Akışlarda kullanılabilen adımların kataloğu. Adımları biz tanımlarız; işletme yalnızca bunlardan seçer, yeni bir
 * ara adım isterse talep açar. Sıra {@link #ordinal()} ile sabittir, işletme sırayı değiştiremez.
 */
public enum OrderFlowStepType {
    ORDER_APPROVAL(
            "Sipariş onayı",
            "Garson",
            "QR'dan gelen sipariş garson onaylayana kadar mutfağa ve hesaba düşmez.",
            EnumSet.of(OrderFlowChannel.TABLE),
            true
    ),
    KITCHEN_PREP(
            "Mutfak hazırlığı",
            "Mutfak",
            "Sipariş mutfak ekranına düşer; mutfak \"Hazır\" deyince garsona veya kuryeye geçer.",
            EnumSet.of(OrderFlowChannel.TABLE, OrderFlowChannel.DELIVERY),
            true
    ),
    PASS_CHECK(
            "Pas kontrolü",
            "Pas sorumlusu",
            "Masanın tüm kalemleri hazır olunca pas sorumlusu kontrol edip garsonu çağırır.",
            EnumSet.of(OrderFlowChannel.TABLE),
            false
    ),
    PACKING(
            "Paketleme kontrolü",
            "Pas / Kasa",
            "Paket çıkmadan kalemler, sos ve çatal-bıçak kontrol listesiyle doğrulanır.",
            EnumSet.of(OrderFlowChannel.DELIVERY),
            false
    ),
    TABLE_SERVICE(
            "Masaya servis",
            "Garson",
            "Garson siparişi masaya götürür ve \"Servis edildi\" der.",
            EnumSet.of(OrderFlowChannel.TABLE),
            true
    ),
    DELIVERY(
            "Kurye teslimatı",
            "Kurye",
            "Kurye paketi alır, \"Yola çık\" ve \"Teslim edildi\" der.",
            EnumSet.of(OrderFlowChannel.DELIVERY),
            true
    );

    private final String label;
    private final String role;
    private final String description;
    private final Set<OrderFlowChannel> channels;
    /** {@code false}: katalogda görünür ama henüz desteklenmiyor; işletme talep açabilir. */
    private final boolean available;

    OrderFlowStepType(String label, String role, String description, Set<OrderFlowChannel> channels, boolean available) {
        this.label = label;
        this.role = role;
        this.description = description;
        this.channels = channels;
        this.available = available;
    }

    public String getLabel() {
        return label;
    }

    public String getRole() {
        return role;
    }

    public String getDescription() {
        return description;
    }

    public Set<OrderFlowChannel> getChannels() {
        return channels;
    }

    public boolean isAvailable() {
        return available;
    }
}
