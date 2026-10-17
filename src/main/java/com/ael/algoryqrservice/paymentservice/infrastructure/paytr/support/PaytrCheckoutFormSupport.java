package com.ael.algoryqrservice.paymentservice.infrastructure.paytr.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.model.AddressInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BasketItemInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BuyerInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentCallbackProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaytrProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaytrHashService;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaytrMerchantOid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PaytrCheckoutFormSupport {

    private final PaytrProperties paytrProperties;
    private final PaymentCallbackProperties paymentCallbackProperties;
    private final PaytrHashService paytrHashService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void requireConfigured() {
        paytrProperties.requireConfigured();
    }

    public String merchantId() {
        return paytrProperties.merchantId();
    }

    public String testModeFlag() {
        return paytrProperties.testMode() ? "1" : "0";
    }

    public String debugOnFlag() {
        return paytrProperties.debugOn() ? "1" : "0";
    }

    public String directPaymentUrl() {
        return paytrProperties.baseUrl() + "/odeme";
    }

    public String sanitizeConversationId(String conversationId) {
        return PaytrMerchantOid.sanitize(conversationId);
    }

    public String requireEmail(BuyerInfo buyer) {
        if (buyer == null || buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            throw new PaymentProcessingException("PAYTR_EMAIL_REQUIRED", "PayTR icin email zorunlu");
        }
        return buyer.getEmail().trim();
    }

    public String resolveIp(BuyerInfo buyer) {
        if (buyer != null && buyer.getIp() != null && !buyer.getIp().isBlank()) {
            return normalizeIpv4(buyer.getIp());
        }
        return "127.0.0.1";
    }

    public String resolveUserName(BuyerInfo buyer) {
        if (buyer == null) {
            return "Musteri";
        }
        String name = ((buyer.getName() == null ? "" : buyer.getName()) + " "
                + (buyer.getSurname() == null ? "" : buyer.getSurname())).trim();
        return name.isBlank() ? "Musteri" : truncate(name, 60);
    }

    public String resolvePhone(BuyerInfo buyer) {
        if (buyer == null || buyer.getGsmNumber() == null || buyer.getGsmNumber().isBlank()) {
            return "5000000000";
        }
        String digits = buyer.getGsmNumber().replaceAll("\\D", "");
        if (digits.startsWith("90") && digits.length() >= 12) {
            digits = "0" + digits.substring(2);
        }
        if (digits.isBlank()) {
            return "5000000000";
        }
        return truncate(digits, 20);
    }

    public String resolveAddress(InitializeCheckoutFormCommand command) {
        return resolveAddress(command.getBillingAddress(), command.getShippingAddress(), command.getBuyer());
    }

    public String resolveAddress(AddressInfo billing, AddressInfo shipping, BuyerInfo buyer) {
        AddressInfo address = billing != null ? billing : shipping;
        if (address == null || address.getAddress() == null || address.getAddress().isBlank()) {
            if (buyer != null && buyer.getRegistrationAddress() != null) {
                return truncate(buyer.getRegistrationAddress(), 400);
            }
            return "Adres belirtilmedi";
        }
        return truncate(address.getAddress(), 400);
    }

    public String encodeBasket(List<BasketItemInfo> items) {
        List<List<Object>> basket = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            basket.add(List.of("Payment", "1.00", 1));
        } else {
            for (BasketItemInfo item : items) {
                String name = item.getName() == null || item.getName().isBlank() ? "Item" : item.getName();
                String price = item.getPrice() == null
                        ? "1.00"
                        : item.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
                basket.add(List.of(name, price, 1));
            }
        }
        try {
            String json = objectMapper.writeValueAsString(basket);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            throw new PaymentProcessingException("PAYTR_BASKET_ENCODE_FAILED", "PayTR sepet encode edilemedi");
        }
    }

    public BigDecimal resolveCheckoutAmount(InitializeCheckoutFormCommand command) {
        return command.getPaidPrice() != null ? command.getPaidPrice() : command.getPrice();
    }

    public String toDecimalPlain(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String toPaytrCurrency(String currency) {
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency) || "TL".equalsIgnoreCase(currency)) {
            return "TL";
        }
        return currency.toUpperCase();
    }

    public String resolveLang(String locale) {
        if (locale != null && locale.toLowerCase().startsWith("en")) {
            return "en";
        }
        return "tr";
    }

    public String buildRedirectUrl(String conversationId, boolean success) {
        return buildRedirectUrl(conversationId, success, false);
    }

    public String buildRedirectUrl(String conversationId, boolean success, boolean verificationOnly) {
        String base = verificationOnly
                ? paymentCallbackProperties.cardVerificationFrontendRedirectUrl()
                : paymentCallbackProperties.frontendRedirectUrl();
        return UriComponentsBuilder.fromUriString(base)
                .queryParam("status", success ? "success" : "failure")
                .queryParam("conversationId", conversationId)
                .build(true)
                .toUriString();
    }

    public String tokenHash(String hashStr) {
        return paytrHashService.tokenHash(hashStr);
    }

    private String normalizeIpv4(String raw) {
        String ip = raw.trim();
        int zone = ip.indexOf('%');
        if (zone >= 0) {
            ip = ip.substring(0, zone);
        }
        if (ip.startsWith("[") && ip.contains("]")) {
            ip = ip.substring(1, ip.indexOf(']'));
        }
        if (ip.contains(":") && !ip.contains(".")) {
            return "127.0.0.1";
        }
        if (ip.matches("\\d{1,3}(\\.\\d{1,3}){3}:\\d+")) {
            ip = ip.substring(0, ip.lastIndexOf(':'));
        }
        if (!ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return "127.0.0.1";
        }
        return ip;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
