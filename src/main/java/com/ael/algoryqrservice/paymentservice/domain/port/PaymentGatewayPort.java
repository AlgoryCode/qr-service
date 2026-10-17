package com.ael.algoryqrservice.paymentservice.domain.port;

import com.ael.algoryqrservice.paymentservice.domain.model.CancelPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.CardStorageSession;
import com.ael.algoryqrservice.paymentservice.domain.model.CompleteThreeDsPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.BinInformation;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InstallmentOptions;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentGatewayResult;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.StoreCardCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.TokenizeCardCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.TokenizedCard;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentGatewayPort {

    PaymentGatewayResult createDirectPayment(CreatePaymentCommand command);

    PaymentGatewayResult initializeThreeDsPayment(CreatePaymentCommand command);

    PaymentGatewayResult completeThreeDsPayment(CompleteThreeDsPaymentCommand command);

    PaymentGatewayResult initializeCheckoutForm(InitializeCheckoutFormCommand command);

    CardStorageSession prepareCardStorage(StoreCardCommand command);

    PaymentGatewayResult retrieveCheckoutForm(String token);

    PaymentGatewayResult retrievePayment(String conversationId, String paymentId);

    PaymentGatewayResult refundPayment(RefundPaymentCommand command);

    PaymentGatewayResult cancelPayment(CancelPaymentCommand command);

    TokenizedCard tokenizeCard(TokenizeCardCommand command);

    List<TokenizedCard> listStoredCards(String cardUserKey);

    void deleteCard(String cardUserKey, String cardToken);

    BinInformation retrieveBin(String binNumber);

    List<InstallmentOptions> retrieveInstallmentOptions(String binNumber, BigDecimal price, String currency);
}
