package com.ael.algoryqrservice.paymentservice.infrastructure.paytr.cardstorage;

import com.ael.algoryqrservice.paymentservice.domain.model.AddressInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BasketItemInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BuyerInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.StoreCardCommand;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.support.PaytrCheckoutFormSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaytrDirectCardStorageRequestMapper {

    private static final String PAYMENT_TYPE_CARD = "card";
    private static final String INSTALLMENT_SINGLE = "0";
    private static final String NON_3D_DISABLED = "0";
    private static final String STORE_CARD_ENABLED = "1";

    private final PaytrCheckoutFormSupport checkoutFormSupport;

    public Map<String, String> toHiddenFields(StoreCardCommand command) {
        checkoutFormSupport.requireConfigured();
        String merchantOid = checkoutFormSupport.sanitizeConversationId(command.getConversationId());
        BuyerInfo buyer = command.getBuyer();
        String email = checkoutFormSupport.requireEmail(buyer);
        BigDecimal amount = command.getPaidPrice() != null ? command.getPaidPrice() : command.getPrice();
        String paymentAmount = checkoutFormSupport.toDecimalPlain(amount);
        String currency = checkoutFormSupport.toPaytrCurrency(command.getCurrency());
        String testMode = checkoutFormSupport.testModeFlag();
        String userIp = checkoutFormSupport.resolveIp(buyer);
        String hashStr = checkoutFormSupport.merchantId()
                + userIp
                + merchantOid
                + email
                + paymentAmount
                + PAYMENT_TYPE_CARD
                + INSTALLMENT_SINGLE
                + currency
                + testMode
                + NON_3D_DISABLED;

        Map<String, String> form = new LinkedHashMap<>();
        form.put("merchant_id", checkoutFormSupport.merchantId());
        form.put("user_ip", userIp);
        form.put("merchant_oid", merchantOid);
        form.put("email", email);
        form.put("payment_type", PAYMENT_TYPE_CARD);
        form.put("payment_amount", paymentAmount);
        form.put("installment_count", INSTALLMENT_SINGLE);
        form.put("currency", currency);
        form.put("test_mode", testMode);
        form.put("non_3d", NON_3D_DISABLED);
        form.put("merchant_ok_url", checkoutFormSupport.buildRedirectUrl(merchantOid, true, true));
        form.put("merchant_fail_url", checkoutFormSupport.buildRedirectUrl(merchantOid, false, true));
        form.put("user_name", checkoutFormSupport.resolveUserName(buyer));
        form.put("user_address", resolveAddress(command.getBillingAddress(), command.getShippingAddress(), buyer));
        form.put("user_phone", checkoutFormSupport.resolvePhone(buyer));
        form.put("user_basket", checkoutFormSupport.encodeBasket(resolveBasket(command.getBasketItems())));
        form.put("debug_on", checkoutFormSupport.debugOnFlag());
        form.put("client_lang", checkoutFormSupport.resolveLang(command.getLocale()));
        form.put("paytr_token", checkoutFormSupport.tokenHash(hashStr));
        form.put("store_card", STORE_CARD_ENABLED);
        log.info(
                "PayTR card form prepared. conversationId={} testMode={} debugOn={} non3d={} "
                        + "storeCard={} emailPresent={} phonePresent={} addressPresent={} basketPresent={} fieldCount={}",
                merchantOid,
                testMode,
                checkoutFormSupport.debugOnFlag(),
                NON_3D_DISABLED,
                STORE_CARD_ENABLED,
                buyer != null && buyer.getEmail() != null && !buyer.getEmail().isBlank(),
                buyer != null && buyer.getGsmNumber() != null && !buyer.getGsmNumber().isBlank(),
                command.getBillingAddress() != null || command.getShippingAddress() != null,
                form.get("user_basket") != null && !form.get("user_basket").isBlank(),
                form.size()
        );
        return form;
    }

    private String resolveAddress(AddressInfo billing, AddressInfo shipping, BuyerInfo buyer) {
        return checkoutFormSupport.resolveAddress(billing, shipping, buyer);
    }

    private List<BasketItemInfo> resolveBasket(List<BasketItemInfo> items) {
        return items == null ? List.of() : items;
    }
}
