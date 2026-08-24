package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.dto.PaymentCardVerificationRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PaymentCardDto;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.util.AppTime;
import com.ael.algoryqrservice.util.IdentityNumbers;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    public PaymentCheckoutFormRequest toCheckoutFormRequest(
            Purchase purchase,
            User user,
            PlanPackage planPackage,
            String clientIp,
            AppProperties appProperties,
            PaymentClientProperties paymentClientProperties
    ) {
        return toDebtCheckoutFormRequest(
                purchase,
                user,
                planPackage,
                clientIp,
                appProperties,
                paymentClientProperties,
                purchase.getPaymentConversationId(),
                1
        );
    }

    public PaymentCheckoutFormRequest toDebtCheckoutFormRequest(
            Purchase purchase,
            User user,
            PlanPackage planPackage,
            String clientIp,
            AppProperties appProperties,
            PaymentClientProperties paymentClientProperties,
            String conversationId,
            int billingCycleNumber
    ) {
        PaymentStyle style = purchase.getPaymentStyle();
        BigDecimal chargeAmount = purchase.getPrice();
        int intervalMonths = purchase.getBillingIntervalMonths() == null ? 1 : purchase.getBillingIntervalMonths();

        Map<String, Object> sourceMetadata = new HashMap<>();
        sourceMetadata.put("userId", user.getId());
        sourceMetadata.put("packageId", planPackage.getId());
        sourceMetadata.put("packageCode", planPackage.getCode());
        sourceMetadata.put("purchaseConversationId", conversationId);
        sourceMetadata.put("purchaseId", purchase.getId());
        sourceMetadata.put("installmentNumber", billingCycleNumber);
        sourceMetadata.put("billingCycleNumber", billingCycleNumber);
        sourceMetadata.put("billingPeriod", purchase.getBillingPeriod() == null ? null : purchase.getBillingPeriod().name());
        sourceMetadata.put("billingIntervalMonths", intervalMonths);
        sourceMetadata.put("paymentStyle", style.name());
        sourceMetadata.put("validityDays", planPackage.getValidityDays());
        sourceMetadata.put("totalAmount", chargeAmount);
        if (purchase.getSubscriptionId() != null) {
            sourceMetadata.put("subscriptionId", purchase.getSubscriptionId());
        }

        return PaymentCheckoutFormRequest.builder()
                .serviceName(appProperties.getServiceName())
                .sourceReferenceId(String.valueOf(purchase.getId()))
                .sourceMetadata(sourceMetadata)
                .conversationId(conversationId)
                .locale("tr")
                .price(chargeAmount)
                .paidPrice(chargeAmount)
                .currency(planPackage.getCurrency())
                .paymentStyle(style.name())
                .subscriptionCycleCount(null)
                .billingIntervalMonths(style == PaymentStyle.SUBSCRIPTION ? intervalMonths : null)
                .basketId("qrpurchase" + purchase.getId())
                .paymentGroup(style == PaymentStyle.SUBSCRIPTION ? "SUBSCRIPTION" : "PRODUCT")
                .provider(blankToNull(paymentClientProperties.getGatewayProvider()))
                .buyer(toBuyer(user, purchase.getBillingSnapshot(), clientIp))
                .shippingAddress(toAddress(purchase.getBillingSnapshot()))
                .billingAddress(toAddress(purchase.getBillingSnapshot()))
                .basketItems(List.of(toBasketItem(planPackage, chargeAmount)))
                .build();
    }

    public PaymentCheckoutFormRequest toPlanChangeCheckoutFormRequest(
            Purchase purchase,
            User user,
            PlanPackage planPackage,
            String clientIp,
            AppProperties appProperties,
            PaymentClientProperties paymentClientProperties,
            String conversationId,
            BigDecimal chargeAmount
    ) {
        if (purchase.getBillingSnapshot() == null) {
            throw new BadRequestException("Fatura bilgisi bulunamadı; önce fatura adresi tanımlayın");
        }
        Map<String, Object> sourceMetadata = new HashMap<>();
        sourceMetadata.put("userId", user.getId());
        sourceMetadata.put("packageId", planPackage.getId());
        sourceMetadata.put("packageCode", planPackage.getCode());
        sourceMetadata.put("purchaseConversationId", conversationId);
        sourceMetadata.put("purchaseId", purchase.getId());
        sourceMetadata.put("installmentNumber", 1);
        sourceMetadata.put("installmentCount", 1);
        sourceMetadata.put("billingCycleNumber", 1);
        sourceMetadata.put("paymentStyle", PaymentStyle.ONE_TIME.name());
        sourceMetadata.put("validityDays", planPackage.getValidityDays());
        sourceMetadata.put("totalAmount", chargeAmount);
        sourceMetadata.put("planChange", true);
        sourceMetadata.put("planChangeDifference", true);

        return PaymentCheckoutFormRequest.builder()
                .serviceName(appProperties.getServiceName())
                .sourceReferenceId(String.valueOf(purchase.getId()))
                .sourceMetadata(sourceMetadata)
                .conversationId(conversationId)
                .locale("tr")
                .price(chargeAmount)
                .paidPrice(chargeAmount)
                .currency(planPackage.getCurrency())
                .paymentStyle(PaymentStyle.ONE_TIME.name())
                .basketId("qrplanchng" + purchase.getId())
                .paymentGroup("PRODUCT")
                .provider(blankToNull(paymentClientProperties.getGatewayProvider()))
                .buyer(toBuyer(user, purchase.getBillingSnapshot(), clientIp))
                .shippingAddress(toAddress(purchase.getBillingSnapshot()))
                .billingAddress(toAddress(purchase.getBillingSnapshot()))
                .basketItems(List.of(toBasketItem(planPackage, chargeAmount, " (fark)")))
                .build();
    }

    public PaymentCheckoutFormRequest toAddonCheckoutFormRequest(
            Purchase purchase,
            User user,
            String productCode,
            String productName,
            String clientIp,
            AppProperties appProperties,
            PaymentClientProperties paymentClientProperties,
            String conversationId,
            LocalDateTime periodStart,
            LocalDateTime periodEnd
    ) {
        if (purchase.getBillingSnapshot() == null) {
            throw new BadRequestException("Fatura bilgisi bulunamadı; önce fatura adresi tanımlayın");
        }
        BigDecimal chargeAmount = purchase.getPrice();
        Map<String, Object> sourceMetadata = new HashMap<>();
        sourceMetadata.put("userId", user.getId());
        sourceMetadata.put("packageId", purchase.getPackageId());
        sourceMetadata.put("packageCode", purchase.getPackageCode());
        sourceMetadata.put("productCode", productCode);
        sourceMetadata.put("purchaseConversationId", conversationId);
        sourceMetadata.put("purchaseId", purchase.getId());
        sourceMetadata.put("installmentNumber", 1);
        sourceMetadata.put("installmentCount", 1);
        sourceMetadata.put("billingCycleNumber", 1);
        sourceMetadata.put("paymentStyle", PaymentStyle.ONE_TIME.name());
        sourceMetadata.put("billingPeriod", purchase.getBillingPeriod() == null ? null : purchase.getBillingPeriod().name());
        sourceMetadata.put("totalAmount", chargeAmount);
        sourceMetadata.put("addon", true);
        sourceMetadata.put("periodStart", periodStart.toString());
        sourceMetadata.put("periodEnd", periodEnd.toString());

        return PaymentCheckoutFormRequest.builder()
                .serviceName(appProperties.getServiceName())
                .sourceReferenceId(String.valueOf(purchase.getId()))
                .sourceMetadata(sourceMetadata)
                .conversationId(conversationId)
                .locale("tr")
                .price(chargeAmount)
                .paidPrice(chargeAmount)
                .currency(purchase.getCurrency())
                .paymentStyle(PaymentStyle.ONE_TIME.name())
                .basketId("qradon" + purchase.getId())
                .paymentGroup("PRODUCT")
                .provider(blankToNull(paymentClientProperties.getGatewayProvider()))
                .buyer(toBuyer(user, purchase.getBillingSnapshot(), clientIp))
                .shippingAddress(toAddress(purchase.getBillingSnapshot()))
                .billingAddress(toAddress(purchase.getBillingSnapshot()))
                .basketItems(List.of(PaymentThreeDsRequest.BasketItemPayload.builder()
                        .id(productCode)
                        .name(productName)
                        .category1("Digital")
                        .category2("Product")
                        .itemType("VIRTUAL")
                        .price(chargeAmount)
                        .build()))
                .build();
    }

    public PaymentCardVerificationRequest toCardVerificationRequest(
            User user,
            BillingSnapshot billingSnapshot,
            String clientIp,
            AppProperties appProperties,
            String conversationId
    ) {
        return PaymentCardVerificationRequest.builder()
                .serviceName(appProperties.getServiceName())
                .sourceReferenceId(String.valueOf(user.getId()))
                .conversationId(conversationId)
                .locale("tr")
                .currency("TRY")
                .buyer(toBuyer(user, billingSnapshot, clientIp))
                .shippingAddress(toAddress(billingSnapshot))
                .billingAddress(toAddress(billingSnapshot))
                .build();
    }

    private static final DateTimeFormatter PAYMENT_ATTEMPT_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String newPaymentAttemptId(Long userId) {
        long safeUserId = userId == null ? 0L : userId;
        String timestamp = AppTime.nowLocal().format(PAYMENT_ATTEMPT_TS);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        return "qr" + safeUserId + timestamp + suffix;
    }

    public String buildConversationId(Long purchaseId) {
        return newPaymentAttemptId(purchaseId);
    }

    public String buildCardVerificationConversationId(Long userId) {
        return newPaymentAttemptId(userId);
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
        String identity = IdentityNumbers.firstOrDefault(address.getTckn(), address.getVkn());
        String gsmNumber = firstNonBlank(address.getPhone(), user.getPhone());
        if (gsmNumber == null) {
            throw new BadRequestException(
                    "Odeme icin fatura adresinizde telefon numarasi bulunmalidir. Lutfen fatura adresinizi guncelleyin."
            );
        }
        String name = firstNonBlank(user.getFirstName(), address.getName(), "Musteri");
        String surname = firstNonBlank(user.getLastName(), address.getSurname(), "Kullanici");
        String registrationAddress = firstNonBlank(address.getAddress(), "Adres bilgisi yok");
        return PaymentThreeDsRequest.BuyerPayload.builder()
                .id(String.valueOf(user.getId()))
                .name(name)
                .surname(surname)
                .gsmNumber(gsmNumber)
                .email(user.getEmail())
                .identityNumber(identity)
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PaymentThreeDsRequest.BasketItemPayload toBasketItem(
            PlanPackage planPackage,
            BigDecimal chargeAmount
    ) {
        return toBasketItem(planPackage, chargeAmount, "");
    }

    private PaymentThreeDsRequest.BasketItemPayload toBasketItem(
            PlanPackage planPackage,
            BigDecimal chargeAmount,
            String nameSuffix
    ) {
        return PaymentThreeDsRequest.BasketItemPayload.builder()
                .id(String.valueOf(planPackage.getId()))
                .name(planPackage.getName() + nameSuffix)
                .category1("Digital")
                .category2("Package")
                .itemType("VIRTUAL")
                .price(chargeAmount)
                .build();
    }
}
