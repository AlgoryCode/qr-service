package com.ael.algoryqrservice.qrservice.client.payment;

import com.ael.algoryqrservice.paymentservice.domain.model.AddressInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BasketItemInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BuyerInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCardInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.CardVerificationInitRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.CreatePaymentMethodRequest;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentCardVerificationRequest;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentThreeDsRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QrPaymentClientMapper {

    public CreatePaymentCommand toCreatePaymentCommand(Long userId, PaymentThreeDsRequest request) {
        CreatePaymentCommand command = CreatePaymentCommand.builder()
                .serviceName(request.getServiceName())
                .sourceReferenceId(request.getSourceReferenceId())
                .sourceMetadata(request.getSourceMetadata())
                .conversationId(request.getConversationId())
                .locale(request.getLocale())
                .price(request.getPrice())
                .paidPrice(request.getPaidPrice())
                .currency(request.getCurrency())
                .paymentStyle(parseStyle(request.getPaymentStyle()))
                .bankInstallmentCount(request.getBankInstallmentCount())
                .subscriptionCycleCount(request.getSubscriptionCycleCount())
                .billingIntervalMonths(request.getBillingIntervalMonths())
                .basketId(request.getBasketId())
                .paymentChannel(request.getPaymentChannel())
                .paymentGroup(request.getPaymentGroup())
                .paymentMethodId(request.getPaymentMethodId())
                .paymentCard(toPaymentCardInfo(request.getPaymentCard()))
                .buyer(toBuyerInfo(request.getBuyer()))
                .shippingAddress(toAddressInfo(request.getShippingAddress()))
                .billingAddress(toAddressInfo(request.getBillingAddress()))
                .basketItems(toBasketItems(request.getBasketItems()))
                .build();
        command.setAccountId(userId == null ? null : String.valueOf(userId));
        return command;
    }

    public InitializeCheckoutFormCommand toInitializeCheckoutFormCommand(Long userId, PaymentCheckoutFormRequest request) {
        InitializeCheckoutFormCommand command = InitializeCheckoutFormCommand.builder()
                .serviceName(request.getServiceName())
                .sourceReferenceId(request.getSourceReferenceId())
                .sourceMetadata(request.getSourceMetadata())
                .conversationId(request.getConversationId())
                .locale(request.getLocale())
                .price(request.getPrice())
                .paidPrice(request.getPaidPrice())
                .currency(request.getCurrency())
                .paymentStyle(parseStyle(request.getPaymentStyle()))
                .subscriptionCycleCount(request.getSubscriptionCycleCount())
                .billingIntervalMonths(request.getBillingIntervalMonths())
                .basketId(request.getBasketId())
                .paymentGroup(request.getPaymentGroup())
                .buyer(toBuyerInfo(request.getBuyer()))
                .shippingAddress(toAddressInfo(request.getShippingAddress()))
                .billingAddress(toAddressInfo(request.getBillingAddress()))
                .basketItems(toBasketItems(request.getBasketItems()))
                .build();
        command.setAccountId(userId == null ? null : String.valueOf(userId));
        return command;
    }

    public CardVerificationInitRequest toCardVerificationInitRequest(PaymentCardVerificationRequest request) {
        CardVerificationInitRequest init = new CardVerificationInitRequest();
        init.setServiceName(request.getServiceName());
        init.setSourceReferenceId(request.getSourceReferenceId());
        init.setConversationId(request.getConversationId());
        init.setLocale(request.getLocale());
        init.setCurrency(request.getCurrency());
        return init;
    }

    public CreatePaymentMethodRequest toCreatePaymentMethodRequest(
            String email,
            String alias,
            String cardHolderName,
            String cardNumber,
            String expireMonth,
            String expireYear
    ) {
        return new CreatePaymentMethodRequest(
                alias == null || alias.isBlank() ? "Kartım" : alias.trim(),
                email,
                cardHolderName,
                cardNumber.replaceAll("\\D", ""),
                expireMonth,
                expireYear
        );
    }

    public RefundPaymentCommand toRefundCommand(String conversationId, java.math.BigDecimal amount, String clientIp) {
        return RefundPaymentCommand.builder()
                .conversationId(conversationId)
                .locale("tr")
                .price(amount)
                .ip(clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp)
                .build();
    }

    private PaymentStyle parseStyle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return PaymentStyle.valueOf(raw.trim());
    }

    private PaymentCardInfo toPaymentCardInfo(PaymentThreeDsRequest.PaymentCardPayload card) {
        if (card == null) {
            return null;
        }
        return PaymentCardInfo.builder()
                .cardHolderName(card.getCardHolderName())
                .cardNumber(card.getCardNumber())
                .expireMonth(card.getExpireMonth())
                .expireYear(card.getExpireYear())
                .cvc(card.getCvc())
                .registerCard(card.getRegisterCard())
                .build();
    }

    private BuyerInfo toBuyerInfo(PaymentThreeDsRequest.BuyerPayload buyer) {
        if (buyer == null) {
            return null;
        }
        return BuyerInfo.builder()
                .id(buyer.getId())
                .name(buyer.getName())
                .surname(buyer.getSurname())
                .gsmNumber(buyer.getGsmNumber())
                .email(buyer.getEmail())
                .identityNumber(buyer.getIdentityNumber())
                .registrationAddress(buyer.getRegistrationAddress())
                .city(buyer.getCity())
                .country(buyer.getCountry())
                .zipCode(buyer.getZipCode())
                .ip(buyer.getIp())
                .build();
    }

    private AddressInfo toAddressInfo(PaymentThreeDsRequest.AddressPayload address) {
        if (address == null) {
            return null;
        }
        return AddressInfo.builder()
                .contactName(address.getContactName())
                .city(address.getCity())
                .country(address.getCountry())
                .address(address.getAddress())
                .zipCode(address.getZipCode())
                .build();
    }

    private List<BasketItemInfo> toBasketItems(List<PaymentThreeDsRequest.BasketItemPayload> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> BasketItemInfo.builder()
                        .id(item.getId())
                        .name(item.getName())
                        .category1(item.getCategory1())
                        .category2(item.getCategory2())
                        .itemType(item.getItemType())
                        .price(item.getPrice())
                        .build())
                .toList();
    }
}
