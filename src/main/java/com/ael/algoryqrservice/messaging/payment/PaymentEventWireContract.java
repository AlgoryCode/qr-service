package com.ael.algoryqrservice.messaging.payment;

public final class PaymentEventWireContract {

    public static final String TYPE_ID = "payment.completed";
    public static final String TYPE_ID_HEADER = "__TypeId__";
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String HEADER_EVENT_TYPE = "eventType";
    public static final String HEADER_PURCHASE_ID = "purchaseId";
    public static final String DEFAULT_EXCHANGE = "payment.events";
    public static final String ROUTING_KEY_SUFFIX = ".payment.events";

    private PaymentEventWireContract() {
    }
}
