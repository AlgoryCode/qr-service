package com.ael.algoryqrservice.client.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PaymentThreeDsRequest {

    private String serviceName;
    private String sourceReferenceId;
    private Map<String, Object> sourceMetadata;
    private String conversationId;
    private String locale;
    private BigDecimal price;
    private BigDecimal paidPrice;
    private String currency;
    private String paymentMode;
    private Integer installmentCount;
    private Integer planInstallmentCount;
    private Integer installmentIntervalMonths;
    private Integer installment;
    private String basketId;
    private String paymentChannel;
    private String paymentGroup;
    private PaymentCardPayload paymentCard;
    private BuyerPayload buyer;
    private AddressPayload shippingAddress;
    private AddressPayload billingAddress;
    private List<BasketItemPayload> basketItems;

    @Data
    @Builder
    public static class PaymentCardPayload {
        private String cardHolderName;
        private String cardNumber;
        private String expireMonth;
        private String expireYear;
        private String cvc;
        private Integer registerCard;
    }

    @Data
    @Builder
    public static class BuyerPayload {
        private String id;
        private String name;
        private String surname;
        private String gsmNumber;
        private String email;
        private String identityNumber;
        private String registrationAddress;
        private String ip;
        private String city;
        private String country;
        private String zipCode;
    }

    @Data
    @Builder
    public static class AddressPayload {
        private String contactName;
        private String city;
        private String country;
        private String address;
        private String zipCode;
    }

    @Data
    @Builder
    public static class BasketItemPayload {
        private String id;
        private String name;
        private String category1;
        private String category2;
        private String itemType;
        private BigDecimal price;
    }
}
