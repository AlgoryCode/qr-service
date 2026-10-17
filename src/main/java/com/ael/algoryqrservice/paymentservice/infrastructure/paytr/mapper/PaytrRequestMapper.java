package com.ael.algoryqrservice.paymentservice.infrastructure.paytr.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.model.AddressInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BasketItemInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BuyerInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCardInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentGatewayResult;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaytrNotificationCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.TokenizedCard;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaytrProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentCallbackProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaytrHashService;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaytrMerchantOid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaytrRequestMapper {

    private static final String SUCCESS = "success";
    private static final String PENDING = "pending";
    private static final String FAILURE_PAYMENT_STATUS = "FAILURE";
    private static final String SUCCESS_PAYMENT_STATUS = "SUCCESS";
    private static final String PAYMENT_TYPE_CARD = "card";
    private static final String NON_3D_ENABLED = "1";

    private final PaytrProperties paytrProperties;
    private final PaymentCallbackProperties paymentCallbackProperties;
    private final PaytrHashService paytrHashService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, String> toGetTokenForm(InitializeCheckoutFormCommand command) {
        paytrProperties.requireConfigured();
        String merchantOid = PaytrMerchantOid.sanitize(command.getConversationId());
        String email = requireEmail(command.getBuyer());
        String paymentAmount = toKurus(resolveAmount(command)).toPlainString();
        String userBasket = encodeBasket(command.getBasketItems());
        String currency = toPaytrCurrency(command.getCurrency());
        String testMode = paytrProperties.testMode() ? "1" : "0";
        String noInstallment = resolveNoInstallment(command);
        String maxInstallment = resolveMaxInstallment(command.getEnabledInstallments());
        String userIp = resolveIp(command.getBuyer());

        String hashStr = paytrProperties.merchantId()
                + userIp
                + merchantOid
                + email
                + paymentAmount
                + userBasket
                + noInstallment
                + maxInstallment
                + currency
                + testMode;

        Map<String, String> form = new LinkedHashMap<>();
        form.put("merchant_id", paytrProperties.merchantId());
        form.put("user_ip", userIp);
        form.put("merchant_oid", merchantOid);
        form.put("email", email);
        form.put("payment_amount", paymentAmount);
        form.put("paytr_token", paytrHashService.tokenHash(hashStr));
        form.put("user_basket", userBasket);
        form.put("debug_on", paytrProperties.debugOn() ? "1" : "0");
        form.put("no_installment", noInstallment);
        form.put("max_installment", maxInstallment);
        form.put("user_name", resolveUserName(command.getBuyer()));
        form.put("user_address", resolveAddress(command));
        form.put("user_phone", resolvePhone(command.getBuyer()));
        form.put("merchant_ok_url", buildRedirectUrl(merchantOid, true, command.isVerificationOnly()));
        form.put("merchant_fail_url", buildRedirectUrl(merchantOid, false, command.isVerificationOnly()));
        form.put("timeout_limit", String.valueOf(paytrProperties.timeoutLimitMinutes()));
        form.put("currency", currency);
        form.put("test_mode", testMode);
        form.put("lang", resolveLang(command.getLocale()));
        return form;
    }

    public Map<String, String> toRefundForm(RefundPaymentCommand command) {
        String merchantOid = PaytrMerchantOid.sanitize(command.getConversationId());
        String returnAmount = command.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("merchant_id", paytrProperties.merchantId());
        form.put("merchant_oid", merchantOid);
        form.put("return_amount", returnAmount);
        form.put("paytr_token", paytrHashService.refundHash(merchantOid, returnAmount));
        return form;
    }

    public PaymentGatewayResult toCheckoutFormResult(InitializeCheckoutFormCommand command, JsonNode response) {
        String status = text(response, "status");
        if (!SUCCESS.equalsIgnoreCase(status)) {
            return PaymentGatewayResult.builder()
                    .conversationId(command.getConversationId())
                    .status(status == null ? "failed" : status)
                    .paymentStatus(FAILURE_PAYMENT_STATUS)
                    .errorCode("PAYTR_TOKEN_FAILED")
                    .errorMessage(text(response, "reason"))
                    .signatureValid(true)
                    .build();
        }
        String token = text(response, "token");
        String iframeUrl = paytrProperties.baseUrl() + "/odeme/guvenli/" + token;
        String html = """
                <script src="%s/js/iframeResizer.min.js"></script>
                <iframe src="%s" id="paytriframe" frameborder="0" scrolling="no" style="width:100%%;"></iframe>
                <script>iFrameResize({},'#paytriframe');</script>
                """.formatted(paytrProperties.baseUrl(), iframeUrl);
        return PaymentGatewayResult.builder()
                .conversationId(command.getConversationId())
                .status(SUCCESS)
                .token(token)
                .paymentPageUrl(iframeUrl)
                .checkoutFormContent(html)
                .htmlContent(html)
                .price(command.getPrice())
                .paidPrice(resolveAmount(command))
                .signatureValid(true)
                .build();
    }

    public PaymentGatewayResult toRefundResult(RefundPaymentCommand command, JsonNode response) {
        String status = text(response, "status");
        boolean success = SUCCESS.equalsIgnoreCase(status);
        return PaymentGatewayResult.builder()
                .conversationId(command.getConversationId())
                .status(status == null ? "failed" : status)
                .paymentStatus(success ? SUCCESS_PAYMENT_STATUS : FAILURE_PAYMENT_STATUS)
                .paidPrice(command.getPrice())
                .errorCode(success ? null : text(response, "err_no"))
                .errorMessage(success ? null : text(response, "err_msg"))
                .signatureValid(true)
                .build();
    }

    public Map<String, String> toStatusQueryForm(String conversationId) {
        paytrProperties.requireConfigured();
        String merchantOid = PaytrMerchantOid.sanitize(conversationId);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("merchant_id", paytrProperties.merchantId());
        form.put("merchant_oid", merchantOid);
        form.put("paytr_token", paytrHashService.statusQueryHash(merchantOid));
        return form;
    }

    public PaymentGatewayResult toStatusQueryResult(String conversationId, JsonNode response) {
        String merchantOid = PaytrMerchantOid.sanitize(conversationId);
        String status = text(response, "status");
        if (!SUCCESS.equalsIgnoreCase(status)) {
            return PaymentGatewayResult.builder()
                    .conversationId(merchantOid)
                    .status(PENDING)
                    .errorCode(text(response, "err_no"))
                    .errorMessage(text(response, "err_msg"))
                    .signatureValid(true)
                    .build();
        }
        BigDecimal paid = parsePaytrAmount(firstNonBlank(
                text(response, "payment_total"),
                text(response, "payment_amount")
        ));
        return PaymentGatewayResult.builder()
                .conversationId(merchantOid)
                .paymentId(merchantOid)
                .paymentTransactionId(merchantOid)
                .status(SUCCESS)
                .paymentStatus(SUCCESS_PAYMENT_STATUS)
                .paidPrice(paid)
                .price(paid)
                .installmentCount(parseInstallmentCount(text(response, "taksit")))
                .lastFourDigits(extractLastFour(text(response, "masked_pan")))
                .signatureValid(true)
                .build();
    }

    public Map<String, String> toStoredCardChargeForm(CreatePaymentCommand command) {
        paytrProperties.requireConfigured();
        String merchantOid = PaytrMerchantOid.sanitize(command.getConversationId());
        String email = requireEmail(command.getBuyer());
        String paymentAmount = resolveAmount(command).setScale(2, RoundingMode.HALF_UP).toPlainString();
        String paymentType = PAYMENT_TYPE_CARD;
        String installmentCount = resolveDirectInstallmentCount(command);
        String currency = toPaytrCurrency(command.getCurrency());
        String testMode = paytrProperties.testMode() ? "1" : "0";
        String userIp = resolveIp(command.getBuyer());
        String hashStr = paytrProperties.merchantId()
                + userIp
                + merchantOid
                + email
                + paymentAmount
                + paymentType
                + installmentCount
                + currency
                + testMode
                + NON_3D_ENABLED;
        PaymentCardInfo card = command.getPaymentCard();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("merchant_id", paytrProperties.merchantId());
        form.put("user_ip", userIp);
        form.put("merchant_oid", merchantOid);
        form.put("email", email);
        form.put("payment_type", paymentType);
        form.put("payment_amount", paymentAmount);
        form.put("installment_count", installmentCount);
        form.put("currency", currency);
        form.put("test_mode", testMode);
        form.put("non_3d", NON_3D_ENABLED);
        form.put("merchant_ok_url", buildRedirectUrl(merchantOid, true));
        form.put("merchant_fail_url", buildRedirectUrl(merchantOid, false));
        form.put("user_name", resolveUserName(command.getBuyer()));
        form.put("user_address", resolveAddress(
                command.getBillingAddress(),
                command.getShippingAddress(),
                command.getBuyer()
        ));
        form.put("user_phone", resolvePhone(command.getBuyer()));
        form.put("user_basket", encodeBasket(command.getBasketItems()));
        form.put("debug_on", paytrProperties.debugOn() ? "1" : "0");
        form.put("client_lang", resolveLang(command.getLocale()));
        form.put("paytr_token", paytrHashService.tokenHash(hashStr));
        form.put("utoken", card.getCardUserKey());
        form.put("ctoken", card.getCardToken());
        if (command.getPaymentStyle() == PaymentStyle.SUBSCRIPTION) {
            form.put("recurring_payment", "1");
        }
        return form;
    }

    public PaymentGatewayResult toStoredCardChargeResult(CreatePaymentCommand command, JsonNode response) {
        String status = text(response, "status");
        if (PENDING.equalsIgnoreCase(status) || status == null) {
            return PaymentGatewayResult.builder()
                    .conversationId(command.getConversationId())
                    .status(PENDING)
                    .htmlContent(text(response, "html"))
                    .signatureValid(true)
                    .build();
        }
        if (!SUCCESS.equalsIgnoreCase(status)) {
            return PaymentGatewayResult.builder()
                    .conversationId(command.getConversationId())
                    .status(status)
                    .paymentStatus(FAILURE_PAYMENT_STATUS)
                    .errorCode(firstNonBlank(text(response, "reason"), text(response, "err_no"), "PAYTR_CHARGE_FAILED"))
                    .errorMessage(firstNonBlank(text(response, "reason"), text(response, "err_msg")))
                    .signatureValid(true)
                    .build();
        }
        return PaymentGatewayResult.builder()
                .conversationId(command.getConversationId())
                .paymentId(PaytrMerchantOid.sanitize(command.getConversationId()))
                .paymentTransactionId(PaytrMerchantOid.sanitize(command.getConversationId()))
                .status(SUCCESS)
                .paymentStatus(SUCCESS_PAYMENT_STATUS)
                .paidPrice(resolveAmount(command))
                .price(command.getPrice())
                .signatureValid(true)
                .build();
    }

    public PaymentGatewayResult toNotificationResult(PaytrNotificationCommand command, boolean hashValid) {
        boolean success = SUCCESS.equalsIgnoreCase(command.status());
        BigDecimal paid = parseKurus(command.totalAmount() != null ? command.totalAmount() : command.paymentAmount());
        return PaymentGatewayResult.builder()
                .conversationId(command.merchantOid())
                .paymentId(command.merchantOid())
                .paymentTransactionId(command.merchantOid())
                .status(success ? SUCCESS : "failed")
                .paymentStatus(success ? SUCCESS_PAYMENT_STATUS : FAILURE_PAYMENT_STATUS)
                .paidPrice(paid)
                .price(paid)
                .errorCode(success ? null : command.failedReasonCode())
                .errorMessage(success ? null : command.failedReasonMsg())
                .cardUserKey(blankToNull(command.utoken()))
                .cardToken(blankToNull(command.ctoken()))
                .lastFourDigits(extractLastFour(command.maskedPan()))
                .installmentCount(parseInstallmentCount(command.installmentCount()))
                .signatureValid(hashValid)
                .build();
    }

    private String encodeBasket(List<BasketItemInfo> items) {
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

    private BigDecimal resolveAmount(InitializeCheckoutFormCommand command) {
        return command.getPaidPrice() != null ? command.getPaidPrice() : command.getPrice();
    }

    private BigDecimal resolveAmount(CreatePaymentCommand command) {
        return command.getPaidPrice() != null ? command.getPaidPrice() : command.getPrice();
    }

    private String resolveNoInstallment(InitializeCheckoutFormCommand command) {
        if (command.getPaymentStyle() == PaymentStyle.BANK_INSTALLMENT) {
            return "0";
        }
        if (command.getEnabledInstallments() != null && !command.getEnabledInstallments().isEmpty()) {
            return "0";
        }
        return "0";
    }

    private String resolveDirectInstallmentCount(CreatePaymentCommand command) {
        if (command.getPaymentStyle() == PaymentStyle.BANK_INSTALLMENT
                && command.getBankInstallmentCount() != null
                && command.getBankInstallmentCount() >= 2) {
            return String.valueOf(command.getBankInstallmentCount());
        }
        return "0";
    }

    private BigDecimal toKurus(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal parseKurus(String kurus) {
        if (kurus == null || kurus.isBlank()) {
            return null;
        }
        return new BigDecimal(kurus.trim()).movePointLeft(2);
    }

    private BigDecimal parsePaytrAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().replace(" ", "").replace(",", ".");
        return new BigDecimal(normalized);
    }

    private Integer parseInstallmentCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String extractLastFour(String maskedPan) {
        if (maskedPan == null || maskedPan.isBlank()) {
            return null;
        }
        String digits = maskedPan.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return null;
        }
        return digits.substring(digits.length() - 4);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toPaytrCurrency(String currency) {
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency) || "TL".equalsIgnoreCase(currency)) {
            return "TL";
        }
        return currency.toUpperCase();
    }

    private String resolveMaxInstallment(List<Integer> enabledInstallments) {
        if (enabledInstallments == null || enabledInstallments.isEmpty()) {
            return "0";
        }
        return enabledInstallments.stream().max(Integer::compareTo).orElse(0).toString();
    }

    private String resolveIp(BuyerInfo buyer) {
        if (buyer != null && buyer.getIp() != null && !buyer.getIp().isBlank()) {
            return normalizeIpv4(buyer.getIp());
        }
        return "127.0.0.1";
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

    private String requireEmail(BuyerInfo buyer) {
        if (buyer == null || buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            throw new PaymentProcessingException("PAYTR_EMAIL_REQUIRED", "PayTR icin email zorunlu");
        }
        return buyer.getEmail().trim();
    }

    private String resolveUserName(BuyerInfo buyer) {
        if (buyer == null) {
            return "Musteri";
        }
        String name = ((buyer.getName() == null ? "" : buyer.getName()) + " "
                + (buyer.getSurname() == null ? "" : buyer.getSurname())).trim();
        return name.isBlank() ? "Musteri" : truncate(name, 60);
    }

    private String resolvePhone(BuyerInfo buyer) {
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

    private String resolveAddress(InitializeCheckoutFormCommand command) {
        return resolveAddress(command.getBillingAddress(), command.getShippingAddress(), command.getBuyer());
    }

    private String resolveAddress(AddressInfo billing, AddressInfo shipping, BuyerInfo buyer) {
        AddressInfo address = billing != null ? billing : shipping;
        if (address == null || address.getAddress() == null || address.getAddress().isBlank()) {
            if (buyer != null && buyer.getRegistrationAddress() != null) {
                return truncate(buyer.getRegistrationAddress(), 400);
            }
            return "Adres belirtilmedi";
        }
        return truncate(address.getAddress(), 400);
    }

    public Map<String, String> toCardListForm(String utoken) {
        paytrProperties.requireConfigured();
        if (utoken == null || utoken.isBlank()) {
            throw new PaymentProcessingException("PAYTR_UTOKEN_REQUIRED", "PayTR kart listesi icin utoken zorunlu");
        }
        log.info("PayTR CAPI LIST request prepared. utokenPresent=true merchantIdPresent={}",
                paytrProperties.merchantId() != null && !paytrProperties.merchantId().isBlank());
        Map<String, String> form = new LinkedHashMap<>();
        form.put("merchant_id", paytrProperties.merchantId());
        form.put("utoken", utoken);
        form.put("paytr_token", paytrHashService.cardListHash(utoken));
        return form;
    }

    public Map<String, String> toCardDeleteForm(String utoken, String ctoken) {
        paytrProperties.requireConfigured();
        if (utoken == null || utoken.isBlank()) {
            throw new PaymentProcessingException("PAYTR_UTOKEN_REQUIRED", "PayTR kart silme icin utoken zorunlu");
        }
        if (ctoken == null || ctoken.isBlank()) {
            throw new PaymentProcessingException("PAYTR_CTOKEN_REQUIRED", "PayTR kart silme icin ctoken zorunlu");
        }
        log.info("PayTR CAPI DELETE request prepared. utokenPresent=true ctokenPresent=true merchantIdPresent={}",
                paytrProperties.merchantId() != null && !paytrProperties.merchantId().isBlank());
        Map<String, String> form = new LinkedHashMap<>();
        form.put("merchant_id", paytrProperties.merchantId());
        form.put("utoken", utoken);
        form.put("ctoken", ctoken);
        form.put("paytr_token", paytrHashService.cardDeleteHash(ctoken, utoken));
        return form;
    }

    public List<TokenizedCard> toCardListResult(String utoken, JsonNode response) {
        if (response == null) {
            return List.of();
        }
        if ("error".equalsIgnoreCase(text(response, "status"))) {
            log.warn(
                    "PayTR CAPI LIST rejected. errNo={} errMsgPresent={}",
                    text(response, "err_no"),
                    text(response, "err_msg") != null && !text(response, "err_msg").isBlank()
            );
            throw new PaymentProcessingException(
                    "PAYTR_CARD_LIST_FAILED",
                    firstNonBlank(text(response, "err_msg"), "PayTR kart listesi alinamadi")
            );
        }
        JsonNode cards = response.get("cards");
        if (cards == null || !cards.isArray()) {
            cards = response.isArray() ? response : null;
        }
        if (cards == null || !cards.isArray()) {
            return List.of();
        }
        List<TokenizedCard> listed = new ArrayList<>();
        for (JsonNode card : cards) {
            String ctoken = firstNonBlank(text(card, "ctoken"), text(card, "c_token"));
            if (ctoken == null) {
                continue;
            }
            listed.add(new TokenizedCard(
                    SUCCESS,
                    null,
                    null,
                    utoken,
                    ctoken,
                    firstNonBlank(text(card, "c_name"), "Kayıtlı kart"),
                    text(card, "c_type"),
                    text(card, "c_brand"),
                    text(card, "c_bank"),
                    firstNonBlank(text(card, "last_4"), extractLastFour(text(card, "masked_pan")))
            ));
        }
        return listed;
    }

    private String buildRedirectUrl(String conversationId, boolean success) {
        return buildRedirectUrl(conversationId, success, false);
    }

    private String buildRedirectUrl(String conversationId, boolean success, boolean verificationOnly) {
        String base = verificationOnly
                ? paymentCallbackProperties.cardVerificationFrontendRedirectUrl()
                : paymentCallbackProperties.frontendRedirectUrl();
        return UriComponentsBuilder.fromUriString(base)
                .queryParam("status", success ? "success" : "failure")
                .queryParam("conversationId", conversationId)
                .build(true)
                .toUriString();
    }

    private String resolveLang(String locale) {
        if (locale != null && locale.toLowerCase().startsWith("en")) {
            return "en";
        }
        return "tr";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
