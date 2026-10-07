package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.PurchaseItem;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PaymentRequestMapper {

    /** PayTR rejects basket lines below this amount. */
    private static final BigDecimal MIN_BASKET_LINE_AMOUNT = new BigDecimal("0.01");

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
        return toDebtCheckoutFormRequest(
                purchase,
                user,
                planPackage,
                clientIp,
                appProperties,
                paymentClientProperties,
                conversationId,
                billingCycleNumber,
                List.of()
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
            int billingCycleNumber,
            List<PurchaseItem> moduleLines
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
        if (purchase.getRecurringPrice() != null) {
            sourceMetadata.put("recurringPrice", purchase.getRecurringPrice());
        }
        if (purchase.getSubscriptionId() != null) {
            sourceMetadata.put("subscriptionId", purchase.getSubscriptionId());
        }
        sourceMetadata.put("cart", cartMetadata(purchase, planPackage, moduleLines));

        return PaymentCheckoutFormRequest.builder()
                .serviceName(appProperties.getServiceName())
                .sourceReferenceId(String.valueOf(purchase.getId()))
                .sourceMetadata(sourceMetadata)
                .conversationId(conversationId)
                .locale("tr")
                .price(chargeAmount)
                .paidPrice(chargeAmount)
                .recurringPrice(resolveRecurringPrice(purchase, chargeAmount))
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
                .basketItems(toBasketItems(planPackage, chargeAmount, moduleLines))
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

    public PaymentCheckoutFormRequest toAddonCartCheckoutFormRequest(
            Purchase purchase,
            User user,
            List<PaymentThreeDsRequest.BasketItemPayload> basketItems,
            String primaryProductCode,
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
        if (basketItems == null || basketItems.isEmpty()) {
            throw new BadRequestException("Sepet kalemi bulunamadı");
        }
        BigDecimal chargeAmount = purchase.getPrice();
        Map<String, Object> sourceMetadata = new HashMap<>();
        sourceMetadata.put("userId", user.getId());
        sourceMetadata.put("packageId", purchase.getPackageId());
        sourceMetadata.put("packageCode", purchase.getPackageCode());
        sourceMetadata.put("productCode", primaryProductCode);
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
                .basketItems(List.copyOf(basketItems))
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
        return toAddonCartCheckoutFormRequest(
                purchase,
                user,
                List.of(PaymentThreeDsRequest.BasketItemPayload.builder()
                        .id(productCode)
                        .name(productName)
                        .category1("Digital")
                        .category2("Product")
                        .itemType("VIRTUAL")
                        .price(purchase.getPrice())
                        .build()),
                productCode,
                clientIp,
                appProperties,
                paymentClientProperties,
                conversationId,
                periodStart,
                periodEnd
        );
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

    /**
     * The renewal amount only needs to travel to the payment service when it differs from what
     * is charged now, which happens once a cart carries ONE_TIME module lines.
     */
    private Map<String, Object> cartMetadata(
            Purchase purchase,
            PlanPackage planPackage,
            List<PurchaseItem> moduleLines
    ) {
        Map<String, Object> packageLine = new LinkedHashMap<>();
        packageLine.put("id", planPackage.getId());
        packageLine.put("code", planPackage.getCode());
        packageLine.put("name", planPackage.getName());
        packageLine.put("billingPeriod", purchase.getBillingPeriod() == null ? null : purchase.getBillingPeriod().name());
        packageLine.put("amount", purchase.getBasePrice() != null ? purchase.getBasePrice() : purchase.getPrice());
        packageLine.put("currency", planPackage.getCurrency());

        List<Map<String, Object>> items = new ArrayList<>();
        if (planPackage.getItems() != null) {
            for (PlanPackageItem item : planPackage.getItems()) {
                if (item.getProduct() == null) {
                    continue;
                }
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("productCode", item.getProduct().getCode());
                line.put("productName", item.getProduct().getName());
                line.put("quantity", item.getQuantity());
                line.put("unlimited", item.isUnlimited());
                items.add(line);
            }
        }

        List<Map<String, Object>> modules = new ArrayList<>();
        if (moduleLines != null) {
            for (PurchaseItem line : moduleLines) {
                Map<String, Object> module = new LinkedHashMap<>();
                module.put("productCode", line.getProductCode());
                module.put("productName", line.getProductName());
                module.put("quantity", line.getQuantity());
                module.put("unitPrice", line.getUnitPrice());
                module.put("vatRate", line.getVatRate());
                module.put("billingType", line.getBillingType() == null ? null : line.getBillingType().name());
                module.put("lineTotal", line.getLineTotal());
                modules.add(module);
            }
        }

        Map<String, Object> cart = new LinkedHashMap<>();
        cart.put("package", packageLine);
        cart.put("items", items);
        cart.put("modules", modules);
        return cart;
    }

    private BigDecimal resolveRecurringPrice(Purchase purchase, BigDecimal chargeAmount) {
        BigDecimal recurringPrice = purchase.getRecurringPrice();
        if (recurringPrice == null
                || recurringPrice.signum() <= 0
                || recurringPrice.compareTo(chargeAmount) == 0) {
            return null;
        }
        return recurringPrice;
    }

    /**
     * Builds the basket for a cart: one line for the package and one per module. The package
     * line absorbs any coupon discount so the lines always add up to {@code chargeAmount},
     * which PayTR hashes alongside the payment amount. Falls back to a single package line
     * when a discount would leave nothing chargeable for the base.
     */
    private List<PaymentThreeDsRequest.BasketItemPayload> toBasketItems(
            PlanPackage planPackage,
            BigDecimal chargeAmount,
            List<PurchaseItem> moduleLines
    ) {
        if (moduleLines == null || moduleLines.isEmpty()) {
            return List.of(toBasketItem(planPackage, chargeAmount));
        }
        BigDecimal modulesTotal = moduleLines.stream()
                .map(PurchaseItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal packageAmount = chargeAmount.subtract(modulesTotal);
        if (packageAmount.compareTo(MIN_BASKET_LINE_AMOUNT) < 0) {
            return List.of(toBasketItem(planPackage, chargeAmount));
        }

        List<PaymentThreeDsRequest.BasketItemPayload> items = new ArrayList<>();
        items.add(toBasketItem(planPackage, packageAmount));
        for (PurchaseItem line : moduleLines) {
            items.add(PaymentThreeDsRequest.BasketItemPayload.builder()
                    .id(line.getProductCode())
                    .name(line.getProductName() + " x" + line.getQuantity())
                    .category1("Digital")
                    .category2("Module")
                    .itemType("VIRTUAL")
                    .price(line.getLineTotal())
                    .build());
        }
        return List.copyOf(items);
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
