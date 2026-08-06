package com.ael.algoryqrservice.client.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PaymentCheckoutFormRequest {

    private String serviceName;
    private String sourceReferenceId;
    private Map<String, Object> sourceMetadata;
    private String conversationId;
    private String locale;
    private BigDecimal price;
    private BigDecimal paidPrice;
    private String currency;
    private String paymentStyle;
    private Integer subscriptionCycleCount;
    private Integer billingIntervalMonths;
    private String basketId;
    private String paymentGroup;
    private PaymentThreeDsRequest.BuyerPayload buyer;
    private PaymentThreeDsRequest.AddressPayload shippingAddress;
    private PaymentThreeDsRequest.AddressPayload billingAddress;
    private List<PaymentThreeDsRequest.BasketItemPayload> basketItems;
}
