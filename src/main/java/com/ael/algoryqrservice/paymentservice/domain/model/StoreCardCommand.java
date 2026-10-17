package com.ael.algoryqrservice.paymentservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreCardCommand {

    private String accountId;
    private String serviceName;
    private String sourceReferenceId;
    private String conversationId;
    private String locale;
    private BigDecimal price;
    private BigDecimal paidPrice;
    private String currency;
    private BuyerInfo buyer;
    private AddressInfo shippingAddress;
    private AddressInfo billingAddress;
    private List<BasketItemInfo> basketItems;
}
