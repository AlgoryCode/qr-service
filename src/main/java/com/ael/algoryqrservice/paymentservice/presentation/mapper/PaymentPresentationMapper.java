package com.ael.algoryqrservice.paymentservice.presentation.mapper;

import com.ael.algoryqrservice.paymentservice.application.dto.CheckoutFormResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.RefundResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.ThreeDsPaymentResultDto;
import com.ael.algoryqrservice.paymentservice.domain.model.AddressInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BasketItemInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BuyerInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCardInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.StoreCardCommand;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.AddressRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.CardVerificationInitRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.BasketItemRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.BuyerRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.CreatePaymentRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.InitializeCheckoutFormRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.PaymentCardRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.RefundPaymentRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.CheckoutFormResponse;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.PaymentResponse;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.RefundResponse;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.ThreeDsPaymentResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PaymentPresentationMapper {

    public CreatePaymentCommand toCreatePaymentCommand(CreatePaymentRequest request) {
        return CreatePaymentCommand.builder()
                .serviceName(request.getServiceName())
                .sourceReferenceId(request.getSourceReferenceId())
                .sourceMetadata(request.getSourceMetadata())
                .conversationId(request.getConversationId())
                .locale(request.getLocale())
                .price(request.getPrice())
                .paidPrice(request.getPaidPrice())
                .currency(request.getCurrency())
                .paymentStyle(request.getPaymentStyle())
                .provider(request.getProvider())
                .bankInstallmentCount(request.getBankInstallmentCount())
                .subscriptionCycleCount(request.getSubscriptionCycleCount())
                .billingIntervalMonths(request.getBillingIntervalMonths())
                .basketId(request.getBasketId())
                .paymentChannel(request.getPaymentChannel())
                .paymentGroup(request.getPaymentGroup())
                .callbackUrl(request.getCallbackUrl())
                .paymentMethodId(request.getPaymentMethodId())
                .paymentCard(toPaymentCardInfo(request.getPaymentCard()))
                .buyer(toBuyerInfo(request.getBuyer()))
                .shippingAddress(toAddressInfo(request.getShippingAddress()))
                .billingAddress(toAddressInfo(request.getBillingAddress()))
                .basketItems(toBasketItemInfos(request.getBasketItems()))
                .build();
    }

    public StoreCardCommand toStoreCardCommand(String accountId, CardVerificationInitRequest request) {
        BigDecimal amount = new BigDecimal("1.00");
        return StoreCardCommand.builder()
                .accountId(accountId)
                .serviceName(request.getServiceName())
                .sourceReferenceId(request.getSourceReferenceId())
                .conversationId(request.getConversationId())
                .locale(request.getLocale())
                .price(amount)
                .paidPrice(amount)
                .currency(request.getCurrency())
                .buyer(toBuyerInfo(request.getBuyer()))
                .shippingAddress(toAddressInfo(request.getShippingAddress()))
                .billingAddress(toAddressInfo(request.getBillingAddress()))
                .basketItems(List.of(BasketItemInfo.builder()
                        .id("card-verification")
                        .name("Kart dogrulama")
                        .category1("Billing")
                        .category2("Card")
                        .itemType("VIRTUAL")
                        .price(amount)
                        .build()))
                .build();
    }

    public InitializeCheckoutFormCommand toInitializeCheckoutFormCommand(InitializeCheckoutFormRequest request) {
        return InitializeCheckoutFormCommand.builder()
                .serviceName(request.getServiceName())
                .sourceReferenceId(request.getSourceReferenceId())
                .sourceMetadata(request.getSourceMetadata())
                .conversationId(request.getConversationId())
                .locale(request.getLocale())
                .price(request.getPrice())
                .paidPrice(request.getPaidPrice())
                .currency(request.getCurrency())
                .paymentStyle(request.getPaymentStyle())
                .subscriptionCycleCount(request.getSubscriptionCycleCount())
                .billingIntervalMonths(request.getBillingIntervalMonths())
                .basketId(request.getBasketId())
                .paymentGroup(request.getPaymentGroup())
                .enabledInstallments(request.getEnabledInstallments())
                .provider(request.getProvider())
                .buyer(toBuyerInfo(request.getBuyer()))
                .shippingAddress(toAddressInfo(request.getShippingAddress()))
                .billingAddress(toAddressInfo(request.getBillingAddress()))
                .basketItems(toBasketItemInfos(request.getBasketItems()))
                .build();
    }

    public RefundPaymentCommand toRefundPaymentCommand(RefundPaymentRequest request) {
        return RefundPaymentCommand.builder()
                .conversationId(request.getConversationId())
                .locale(request.getLocale())
                .price(request.getPrice())
                .ip(request.getIp())
                .build();
    }

    public PaymentResponse toPaymentResponse(PaymentResultDto paymentResultDto) {
        return PaymentResponse.builder()
                .conversationId(paymentResultDto.getConversationId())
                .paymentId(paymentResultDto.getPaymentId())
                .paymentTransactionId(paymentResultDto.getPaymentTransactionId())
                .basketId(paymentResultDto.getBasketId())
                .accountId(paymentResultDto.getAccountId())
                .buyerEmail(paymentResultDto.getBuyerEmail())
                .buyerName(paymentResultDto.getBuyerName())
                .serviceName(paymentResultDto.getServiceName())
                .sourceReferenceId(paymentResultDto.getSourceReferenceId())
                .price(paymentResultDto.getPrice())
                .paidPrice(paymentResultDto.getPaidPrice())
                .refundedAmount(paymentResultDto.getRefundedAmount())
                .currency(paymentResultDto.getCurrency())
                .status(paymentResultDto.getStatus())
                .paymentType(paymentResultDto.getPaymentType())
                .paymentStyle(paymentResultDto.getPaymentStyle())
                .bankInstallmentCount(paymentResultDto.getBankInstallmentCount())
                .subscriptionId(paymentResultDto.getSubscriptionId())
                .billingCycleNumber(paymentResultDto.getBillingCycleNumber())
                .verificationOnly(paymentResultDto.isVerificationOnly())
                .errorCode(paymentResultDto.getErrorCode())
                .errorMessage(paymentResultDto.getErrorMessage())
                .createdAt(paymentResultDto.getCreatedAt())
                .updatedAt(paymentResultDto.getUpdatedAt())
                .build();
    }

    public CheckoutFormResponse toCheckoutFormResponse(CheckoutFormResultDto checkoutFormResultDto) {
        return CheckoutFormResponse.builder()
                .conversationId(checkoutFormResultDto.getConversationId())
                .token(checkoutFormResultDto.getToken())
                .paymentPageUrl(checkoutFormResultDto.getPaymentPageUrl())
                .checkoutFormContent(checkoutFormResultDto.getCheckoutFormContent())
                .build();
    }

    public ThreeDsPaymentResponse toThreeDsPaymentResponse(ThreeDsPaymentResultDto threeDsPaymentResultDto) {
        return ThreeDsPaymentResponse.builder()
                .conversationId(threeDsPaymentResultDto.getConversationId())
                .htmlContent(threeDsPaymentResultDto.getHtmlContent())
                .build();
    }

    public RefundResponse toRefundResponse(RefundResultDto refundResultDto) {
        return RefundResponse.builder()
                .conversationId(refundResultDto.getConversationId())
                .paymentTransactionId(refundResultDto.getPaymentTransactionId())
                .refundedPrice(refundResultDto.getRefundedPrice())
                .totalRefundedAmount(refundResultDto.getTotalRefundedAmount())
                .status(refundResultDto.getStatus())
                .build();
    }

    private PaymentCardInfo toPaymentCardInfo(PaymentCardRequest request) {
        if (request == null) {
            return null;
        }
        return PaymentCardInfo.builder()
                .cardHolderName(request.getCardHolderName())
                .cardNumber(request.getCardNumber())
                .expireMonth(request.getExpireMonth())
                .expireYear(request.getExpireYear())
                .cvc(request.getCvc())
                .registerCard(request.getRegisterCard())
                .build();
    }

    public BuyerInfo toBuyerInfo(BuyerRequest request) {
        if (request == null) {
            return null;
        }
        return BuyerInfo.builder()
                .id(request.getId())
                .name(request.getName())
                .surname(request.getSurname())
                .gsmNumber(request.getGsmNumber())
                .email(request.getEmail())
                .identityNumber(request.getIdentityNumber())
                .registrationAddress(request.getRegistrationAddress())
                .ip(request.getIp())
                .city(request.getCity())
                .country(request.getCountry())
                .zipCode(request.getZipCode())
                .build();
    }

    public AddressInfo toAddressInfo(AddressRequest request) {
        if (request == null) {
            return null;
        }
        return AddressInfo.builder()
                .contactName(request.getContactName())
                .city(request.getCity())
                .country(request.getCountry())
                .address(request.getAddress())
                .zipCode(request.getZipCode())
                .build();
    }

    private List<BasketItemInfo> toBasketItemInfos(List<BasketItemRequest> requests) {
        return requests.stream()
                .map(this::toBasketItemInfo)
                .toList();
    }

    private BasketItemInfo toBasketItemInfo(BasketItemRequest request) {
        return BasketItemInfo.builder()
                .id(request.getId())
                .name(request.getName())
                .category1(request.getCategory1())
                .category2(request.getCategory2())
                .itemType(request.getItemType())
                .price(request.getPrice())
                .build();
    }
}
