package com.ael.algoryqrservice.paymentservice.infrastructure.paytr.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.ael.algoryqrservice.paymentservice.domain.model.CheckoutFormKind;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentGatewayResult;
import com.ael.algoryqrservice.paymentservice.domain.port.CheckoutFormInitializerPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.client.PaytrApiClient;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.mapper.PaytrRequestMapper;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.support.PaytrCheckoutFormSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaytrIframeCheckoutInitializer implements CheckoutFormInitializerPort {

    private final PaytrApiClient paytrApiClient;
    private final PaytrRequestMapper paytrRequestMapper;
    private final PaytrCheckoutFormSupport checkoutFormSupport;

    @Override
    public CheckoutFormKind kind() {
        return CheckoutFormKind.IFRAME;
    }

    @Override
    public PaymentGatewayResult initialize(InitializeCheckoutFormCommand command) {
        String merchantOid = checkoutFormSupport.sanitizeConversationId(command.getConversationId());
        command.setConversationId(merchantOid);
        Map<String, String> form = paytrRequestMapper.toGetTokenForm(command);
        JsonNode response = paytrApiClient.getToken(form);
        String status = response.path("status").asText(null);
        String reason = response.path("reason").asText(null);
        if (!"success".equalsIgnoreCase(status)) {
            log.warn(
                    "PayTR get-token failed. merchantOid={} merchantId={} testMode={} userIp={} paymentAmount={} status={} reason={}",
                    merchantOid,
                    form.get("merchant_id"),
                    form.get("test_mode"),
                    form.get("user_ip"),
                    form.get("payment_amount"),
                    status,
                    reason
            );
        } else {
            log.info("PayTR get-token success. merchantOid={}", merchantOid);
        }
        return paytrRequestMapper.toCheckoutFormResult(command, response);
    }
}
