package com.ael.algoryqrservice.paymentservice.infrastructure.paytr.cardstorage;

import com.ael.algoryqrservice.paymentservice.domain.model.CardStorageSession;
import com.ael.algoryqrservice.paymentservice.domain.model.StoreCardCommand;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.support.PaytrCheckoutFormSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaytrDirectCardStorageFormFactory {

    private final PaytrDirectCardStorageRequestMapper requestMapper;
    private final PaytrCheckoutFormSupport checkoutFormSupport;

    public CardStorageSession create(StoreCardCommand command) {
        String merchantOid = checkoutFormSupport.sanitizeConversationId(command.getConversationId());
        command.setConversationId(merchantOid);
        Map<String, String> fields = requestMapper.toHiddenFields(command);
        return new CardStorageSession(merchantOid, checkoutFormSupport.directPaymentUrl(), fields);
    }
}
