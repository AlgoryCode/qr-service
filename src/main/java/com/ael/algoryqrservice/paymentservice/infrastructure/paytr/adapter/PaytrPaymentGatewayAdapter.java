package com.ael.algoryqrservice.paymentservice.infrastructure.paytr.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.model.BinInformation;
import com.ael.algoryqrservice.paymentservice.domain.model.CancelPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.CardStorageSession;
import com.ael.algoryqrservice.paymentservice.domain.model.CompleteThreeDsPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InstallmentOptions;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCardInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentGatewayResult;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.StoreCardCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.TokenizeCardCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.TokenizedCard;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentGatewayPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaytrMerchantOid;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.cardstorage.PaytrDirectCardStorageFormFactory;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.checkout.CheckoutFormInitializerFactory;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.client.PaytrApiClient;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.mapper.PaytrRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("paytrPaymentGateway")
@RequiredArgsConstructor
public class PaytrPaymentGatewayAdapter implements PaymentGatewayPort {

    private final PaytrApiClient paytrApiClient;
    private final PaytrRequestMapper paytrRequestMapper;
    private final CheckoutFormInitializerFactory checkoutFormInitializerFactory;
    private final PaytrDirectCardStorageFormFactory directCardStorageFormFactory;

    @Override
    public PaymentGatewayResult createDirectPayment(CreatePaymentCommand command) {
        requireStoredCard(command.getPaymentCard());
        String merchantOid = PaytrMerchantOid.sanitize(command.getConversationId());
        command.setConversationId(merchantOid);
        Map<String, String> form = paytrRequestMapper.toStoredCardChargeForm(command);
        JsonNode response = paytrApiClient.chargeStoredCard(form);
        log.info(
                "Stored-card charge submitted. merchantId={} paymentId={} status={}",
                command.getAccountId(),
                merchantOid,
                response.path("status").asText(null)
        );
        return paytrRequestMapper.toStoredCardChargeResult(command, response);
    }

    @Override
    public PaymentGatewayResult initializeThreeDsPayment(CreatePaymentCommand command) {
        throw unsupported("THREE_DS");
    }

    @Override
    public PaymentGatewayResult completeThreeDsPayment(CompleteThreeDsPaymentCommand command) {
        throw unsupported("THREE_DS_COMPLETE");
    }

    @Override
    public PaymentGatewayResult initializeCheckoutForm(InitializeCheckoutFormCommand command) {
        return checkoutFormInitializerFactory.initialize(command);
    }

    @Override
    public CardStorageSession prepareCardStorage(StoreCardCommand command) {
        CardStorageSession session = directCardStorageFormFactory.create(command);
        log.info(
                "Card storage session ready. merchantId={} paymentId={}",
                command.getAccountId(),
                session.conversationId()
        );
        return session;
    }

    @Override
    public PaymentGatewayResult retrieveCheckoutForm(String token) {
        throw unsupported("RETRIEVE_CHECKOUT_FORM");
    }

    @Override
    public PaymentGatewayResult retrievePayment(String conversationId, String paymentId) {
        Map<String, String> form = paytrRequestMapper.toStatusQueryForm(conversationId);
        JsonNode response = paytrApiClient.queryStatus(form);
        log.debug(
                "PayTR durum-sorgu response received. conversationId={} status={}",
                conversationId,
                response.path("status").asText(null)
        );
        return paytrRequestMapper.toStatusQueryResult(conversationId, response);
    }

    @Override
    public PaymentGatewayResult refundPayment(RefundPaymentCommand command) {
        Map<String, String> form = paytrRequestMapper.toRefundForm(command);
        JsonNode response = paytrApiClient.refund(form);
        String status = response.path("status").asText(null);
        if (!"success".equalsIgnoreCase(status)) {
            log.warn(
                    "Refund rejected by gateway. paymentId={} status={} reason={}",
                    command.getConversationId(),
                    status,
                    response.path("err_no").asText(null)
            );
        }
        return paytrRequestMapper.toRefundResult(command, response);
    }

    @Override
    public PaymentGatewayResult cancelPayment(CancelPaymentCommand command) {
        throw unsupported("CANCEL");
    }

    @Override
    public TokenizedCard tokenizeCard(TokenizeCardCommand command) {
        throw unsupported("TOKENIZE_CARD");
    }

    @Override
    public java.util.List<TokenizedCard> listStoredCards(String cardUserKey) {
        Map<String, String> form = paytrRequestMapper.toCardListForm(cardUserKey);
        JsonNode response = paytrApiClient.listStoredCards(form);
        return paytrRequestMapper.toCardListResult(cardUserKey, response);
    }

    @Override
    public void deleteCard(String cardUserKey, String cardToken) {
        Map<String, String> form = paytrRequestMapper.toCardDeleteForm(cardUserKey, cardToken);
        JsonNode response = paytrApiClient.deleteStoredCard(form);
        if (!"success".equalsIgnoreCase(response.path("status").asText(null))) {
            String reason = response.path("err_msg").asText(null);
            log.warn(
                    "PayTR card delete rejected. utokenPresent=true ctokenPresent=true errNo={} errMsgPresent={}",
                    response.path("err_no").asText(null),
                    reason != null && !reason.isBlank()
            );
            throw new PaymentProcessingException(
                    "PAYTR_CARD_DELETE_FAILED",
                    reason == null || reason.isBlank() ? "PayTR kart silme islemini reddetti" : reason
            );
        }
        log.info("PayTR card delete succeeded. utokenPresent=true ctokenPresent=true");
    }

    @Override
    public BinInformation retrieveBin(String binNumber) {
        throw unsupported("BIN");
    }

    @Override
    public List<InstallmentOptions> retrieveInstallmentOptions(String binNumber, BigDecimal price, String currency) {
        throw unsupported("INSTALLMENTS");
    }

    private void requireStoredCard(PaymentCardInfo card) {
        if (card == null || isBlank(card.getCardUserKey()) || isBlank(card.getCardToken())) {
            throw new PaymentProcessingException(
                    "PAYTR_STORED_CARD_REQUIRED",
                    "PayTR kayitli kart odemesi icin utoken ve ctoken zorunlu"
            );
        }
        if (!isBlank(card.getCardNumber()) || !isBlank(card.getCvc())) {
            throw new PaymentProcessingException(
                    "PAYTR_RAW_CARD_FORBIDDEN",
                    "PayTR ham kart verisi kabul etmez; iFrame veya kayitli kart kullanin"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private PaymentProcessingException unsupported(String operation) {
        return new PaymentProcessingException(
                "PAYTR_UNSUPPORTED_OPERATION",
                "PayTR bu islemi desteklemiyor: " + operation
        );
    }
}
