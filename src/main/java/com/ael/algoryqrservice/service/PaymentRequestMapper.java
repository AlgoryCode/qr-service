package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PaymentCardDto;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
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
        PaymentStyle style = purchase.getPaymentStyle();
        BigDecimal chargeAmount = planPackage.getPrice();
        int bankInstallments = style == PaymentStyle.BANK_INSTALLMENT
                ? request.resolvedInstallmentCount()
                : 1;

        return PaymentThreeDsRequest.builder()
                .serviceName(appProperties.getServiceName())
                .sourceReferenceId(String.valueOf(purchase.getId()))
                .sourceMetadata(Map.of(
                        "userId", user.getId(),
                        "packageId", planPackage.getId(),
                        "packageCode", planPackage.getCode(),
                        "purchaseConversationId", purchase.getPaymentConversationId(),
                        "installmentNumber", 1,
                        "installmentCount", 1,
                        "bankInstallmentCount", bankInstallments,
                        "paymentStyle", style.name(),
                        "validityDays", planPackage.getValidityDays(),
                        "totalAmount", planPackage.getPrice()
                ))
                .conversationId(purchase.getPaymentConversationId())
                .locale("tr")
                .price(chargeAmount)
                .paidPrice(chargeAmount)
                .currency(planPackage.getCurrency())
                .paymentMode(request.getPaymentMode().name())
                .paymentStyle(style.name())
                .installmentCount(1)
                .bankInstallmentCount(style == PaymentStyle.BANK_INSTALLMENT ? bankInstallments : null)
                .subscriptionCycleCount(style == PaymentStyle.SUBSCRIPTION ? 12 : null)
                .billingIntervalMonths(style == PaymentStyle.SUBSCRIPTION ? 1 : null)
                .installment(bankInstallments)
                .basketId("qr-purchase-" + purchase.getId())
                .paymentChannel("WEB")
                .paymentGroup(style == PaymentStyle.SUBSCRIPTION ? "SUBSCRIPTION" : "PRODUCT")
                .paymentCard(request.getPaymentCard() == null ? null : toPaymentCard(request.getPaymentCard()))
                .paymentMethodId(request.getPaymentMethodId())
                .buyer(toBuyer(user, purchase.getBillingSnapshot(), clientIp))
                .shippingAddress(toAddress(purchase.getBillingSnapshot()))
                .billingAddress(toAddress(purchase.getBillingSnapshot()))
                .basketItems(List.of(toBasketItem(planPackage, chargeAmount)))
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

    private PaymentThreeDsRequest.BuyerPayload toBuyer(User user, BillingSnapshot address, String clientIp) {
        String identity = address.getTckn() != null ? address.getTckn() : address.getVkn();
        return PaymentThreeDsRequest.BuyerPayload.builder()
                .id(String.valueOf(user.getId()))
                .name(user.getFirstName())
                .surname(user.getLastName())
                .gsmNumber(user.getPhone())
                .email(user.getEmail())
                .identityNumber(identity != null ? identity : "11111111111")
                .registrationAddress(address.getAddress())
                .ip(clientIp != null && !clientIp.isBlank() ? clientIp : "127.0.0.1")
                .city(address.getCity())
                .country(address.getCountry())
                .zipCode(address.getPostcode())
                .build();
    }

    private PaymentThreeDsRequest.AddressPayload toAddress(BillingSnapshot address) {
        return PaymentThreeDsRequest.AddressPayload.builder()
                .contactName(address.getLegalName() != null
                        ? address.getLegalName()
                        : String.join(" ", value(address.getName()), value(address.getSurname())).trim())
                .city(address.getCity())
                .country(address.getCountry())
                .address(address.getAddress())
                .zipCode(address.getPostcode())
                .build();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private PaymentThreeDsRequest.BasketItemPayload toBasketItem(
            PlanPackage planPackage,
            BigDecimal chargeAmount
    ) {
        return PaymentThreeDsRequest.BasketItemPayload.builder()
                .id(String.valueOf(planPackage.getId()))
                .name(planPackage.getName())
                .category1("Digital")
                .category2("Package")
                .itemType("VIRTUAL")
                .price(chargeAmount)
                .build();
    }
}
