package com.ael.algoryqrservice.qrservice.orderflow;

import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowChannel;
import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowStepType;

import java.util.List;
import java.util.Map;

import static com.ael.algoryqrservice.qrservice.model.enums.OrderFlowStepType.DELIVERY;
import static com.ael.algoryqrservice.qrservice.model.enums.OrderFlowStepType.KITCHEN_PREP;
import static com.ael.algoryqrservice.qrservice.model.enums.OrderFlowStepType.TABLE_SERVICE;

/** İşletmenin akış kurarken başladığı hazır şablonlar; seçildikten sonra adımlar açılıp kapatılabilir. */
public enum OrderFlowTemplate {
    SIMPLE_CAFE(
            "Basit kafe",
            "Garson siparişi alır ve kendisi servis eder; mutfak ekranı yok.",
            Map.of(OrderFlowChannel.TABLE, List.of(TABLE_SERVICE))
    ),
    RESTAURANT(
            "Restoran",
            "Garson sipariş alır, mutfak hazırlar, garson servis eder.",
            Map.of(OrderFlowChannel.TABLE, List.of(KITCHEN_PREP, TABLE_SERVICE))
    ),
    RESTAURANT_DELIVERY(
            "Restoran + paket",
            "Restoran akışına ek olarak paket siparişleri mutfaktan kuryeye gider.",
            Map.of(
                    OrderFlowChannel.TABLE, List.of(KITCHEN_PREP, TABLE_SERVICE),
                    OrderFlowChannel.DELIVERY, List.of(KITCHEN_PREP, DELIVERY)
            )
    ),
    DELIVERY_ONLY(
            "Sadece paket",
            "Salon yok; siparişler mutfakta hazırlanır ve kuryeyle gider.",
            Map.of(OrderFlowChannel.DELIVERY, List.of(KITCHEN_PREP, DELIVERY))
    );

    private final String label;
    private final String description;
    private final Map<OrderFlowChannel, List<OrderFlowStepType>> steps;

    OrderFlowTemplate(String label, String description, Map<OrderFlowChannel, List<OrderFlowStepType>> steps) {
        this.label = label;
        this.description = description;
        this.steps = steps;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public List<OrderFlowStepType> stepsFor(OrderFlowChannel channel) {
        return steps.getOrDefault(channel, List.of());
    }
}
