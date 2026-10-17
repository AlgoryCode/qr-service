package com.ael.algoryqrservice.paymentservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitializeCheckoutFormCommand {

    private String accountId;
    private String serviceName;
    private String sourceReferenceId;
    private Map<String, Object> sourceMetadata;
    private String conversationId;
    private String locale;
    private BigDecimal price;
    private BigDecimal paidPrice;
    private String currency;
    private PaymentStyle paymentStyle;
    private Integer subscriptionCycleCount;
    private Integer billingIntervalMonths;
    private String subscriptionId;
    private Integer billingCycleNumber;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String basketId;
    private String paymentGroup;
    private String callbackUrl;
    private PaymentProvider provider;
    private List<Integer> enabledInstallments;
    private BuyerInfo buyer;
    private AddressInfo shippingAddress;
    private AddressInfo billingAddress;
    private List<BasketItemInfo> basketItems;

    private boolean verificationOnly;
}
