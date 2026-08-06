package com.ael.algoryqrservice.client.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentCardVerificationRequest {

    private String serviceName;
    private String sourceReferenceId;
    private String conversationId;
    private String locale;
    private String currency;
    private PaymentThreeDsRequest.BuyerPayload buyer;
    private PaymentThreeDsRequest.AddressPayload shippingAddress;
    private PaymentThreeDsRequest.AddressPayload billingAddress;
}
