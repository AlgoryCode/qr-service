package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AddressDto;
import com.ael.algoryqrservice.model.dto.PaymentCardDto;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PaymentRequestMapper {

    public PaymentThreeDsRequest toThreeDsRequest(
            Purchase purchase,
            User user,
            PlanPackage planPackage,
            PurchaseRequest request,
            String clientIp,
            AppProperties appProperties
    ) {
        AddressDto shippingAddress = request.getShippingAddress() != null
                ? request.getShippingAddress()
                : request.getBillingAddress();

        return PaymentThreeDsRequest.builder()
                .serviceName(appProperties.getServiceName())
                .sourceReferenceId(String.valueOf(purchase.getId()))
                .sourceMetadata(Map.of(
                        "userId", user.getId(),
                        "packageId", planPackage.getId()
                ))
                .conversationId(purchase.getPaymentConversationId())
                .locale("tr")
                .price(planPackage.getPrice())
                .paidPrice(planPackage.getPrice())
                .currency(planPackage.getCurrency())
                .installment(1)
                .basketId("qr-purchase-" + purchase.getId())
                .paymentChannel("WEB")
                .paymentGroup("PRODUCT")
                .paymentCard(toPaymentCard(request.getPaymentCard()))
                .buyer(toBuyer(user, request, clientIp))
                .shippingAddress(toAddress(shippingAddress))
                .billingAddress(toAddress(request.getBillingAddress()))
                .basketItems(List.of(toBasketItem(planPackage)))
                .build();
    }

    public String buildConversationId(Long purchaseId) {
        return "qr-purchase-" + purchaseId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private PaymentThreeDsRequest.PaymentCardPayload toPaymentCard(PaymentCardDto card) {
        return PaymentThreeDsRequest.PaymentCardPayload.builder()
                .cardHolderName(card.getCardHolderName())
                .cardNumber(card.getCardNumber())
                .expireMonth(card.getExpireMonth())
                .expireYear(card.getExpireYear())
                .cvc(card.getCvc())
                .registerCard(card.getRegisterCard() != null ? card.getRegisterCard() : 0)
                .build();
    }

    private PaymentThreeDsRequest.BuyerPayload toBuyer(User user, PurchaseRequest request, String clientIp) {
        return PaymentThreeDsRequest.BuyerPayload.builder()
                .id(String.valueOf(user.getId()))
                .name(user.getFirstName())
                .surname(user.getLastName())
                .gsmNumber(user.getPhone())
                .email(user.getEmail())
                .identityNumber(request.getIdentityNumber())
                .registrationAddress(request.getBillingAddress().getAddress())
                .ip(clientIp != null && !clientIp.isBlank() ? clientIp : "127.0.0.1")
                .city(request.getBillingAddress().getCity())
                .country(request.getBillingAddress().getCountry())
                .zipCode(request.getBillingAddress().getZipCode())
                .build();
    }

    private PaymentThreeDsRequest.AddressPayload toAddress(AddressDto address) {
        return PaymentThreeDsRequest.AddressPayload.builder()
                .contactName(address.getContactName())
                .city(address.getCity())
                .country(address.getCountry())
                .address(address.getAddress())
                .zipCode(address.getZipCode())
                .build();
    }

    private PaymentThreeDsRequest.BasketItemPayload toBasketItem(PlanPackage planPackage) {
        return PaymentThreeDsRequest.BasketItemPayload.builder()
                .id(String.valueOf(planPackage.getId()))
                .name(planPackage.getName())
                .category1("Digital")
                .category2("Package")
                .itemType("VIRTUAL")
                .price(planPackage.getPrice())
                .build();
    }
}
