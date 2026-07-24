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
import java.util.HashMap;
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
        BigDecimal chargeAmount = purchase.getPrice();
        int intervalMonths = purchase.getBillingIntervalMonths() == null ? 1 : purchase.getBillingIntervalMonths();

        Map<String, Object> sourceMetadata = new HashMap<>();
        sourceMetadata.put("userId", user.getId());
        sourceMetadata.put("packageId", planPackage.getId());
        sourceMetadata.put("packageCode", planPackage.getCode());
        sourceMetadata.put("purchaseConversationId", purchase.getPaymentConversationId());
        sourceMetadata.put("purchaseId", purchase.getId());
        sourceMetadata.put("installmentNumber", 1);
        sourceMetadata.put("billingCycleNumber", 1);
        sourceMetadata.put("billingPeriod", purchase.getBillingPeriod() == null ? null : purchase.getBillingPeriod().name());
        sourceMetadata.put("billingIntervalMonths", intervalMonths);
        sourceMetadata.put("paymentStyle", style.name());
        sourceMetadata.put("validityDays", planPackage.getValidityDays());
        sourceMetadata.put("totalAmount", chargeAmount);

        return PaymentThreeDsRequest.builder()
                .serviceName(appProperties.getServiceName())
                .sourceReferenceId(String.valueOf(purchase.getId()))
                .sourceMetadata(sourceMetadata)
                .conversationId(purchase.getPaymentConversationId())
                .locale("tr")
                .price(chargeAmount)
                .paidPrice(chargeAmount)
                .currency(planPackage.getCurrency())
                .paymentMode(request.getPaymentMode().name())
                .paymentStyle(style.name())
                .installmentCount(1)
                .bankInstallmentCount(null)
                .subscriptionCycleCount(null)
                .billingIntervalMonths(style == PaymentStyle.SUBSCRIPTION ? intervalMonths : null)
                .installment(1)
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
        String name = firstNonBlank(user.getFirstName(), address.getName(), "Musteri");
        String surname = firstNonBlank(user.getLastName(), address.getSurname(), "Kullanici");
        String registrationAddress = firstNonBlank(address.getAddress(), "Adres bilgisi yok");
        return PaymentThreeDsRequest.BuyerPayload.builder()
                .id(String.valueOf(user.getId()))
                .name(name)
                .surname(surname)
                .gsmNumber(firstNonBlank(user.getPhone(), "5000000000"))
                .email(user.getEmail())
                .identityNumber(firstNonBlank(identity, "11111111111"))
                .registrationAddress(registrationAddress)
                .ip(clientIp != null && !clientIp.isBlank() ? clientIp : "127.0.0.1")
                .city(firstNonBlank(address.getCity(), "Istanbul"))
                .country(firstNonBlank(address.getCountry(), "Turkey"))
                .zipCode(address.getPostcode())
                .build();
    }

    private PaymentThreeDsRequest.AddressPayload toAddress(BillingSnapshot address) {
        String contactName = firstNonBlank(
                address.getLegalName(),
                String.join(" ", value(address.getName()), value(address.getSurname())).trim(),
                "Musteri"
        );
        return PaymentThreeDsRequest.AddressPayload.builder()
                .contactName(contactName)
                .city(firstNonBlank(address.getCity(), "Istanbul"))
                .country(firstNonBlank(address.getCountry(), "Turkey"))
                .address(firstNonBlank(address.getAddress(), "Adres bilgisi yok"))
                .zipCode(address.getPostcode())
                .build();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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
