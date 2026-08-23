package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.client.dto.PaymentCardVerificationRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.BillingAddressType;
import com.ael.algoryqrservice.service.BillingAddressService;
import com.ael.algoryqrservice.service.PaymentRequestMapper;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingPaymentControllerTest {

    @Test
    void initiateCardVerification_whenAuthenticated_thenResolveDefaultAddressAndDelegateToPaymentService() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        BillingAddressService billingAddressService = mock(BillingAddressService.class);
        PaymentRequestMapper paymentRequestMapper = mock(PaymentRequestMapper.class);
        AppProperties appProperties = new AppProperties();
        BillingPaymentController controller = new BillingPaymentController(
                paymentServiceClient, securityUtils, billingAddressService, paymentRequestMapper, appProperties
        );

        User user = User.builder().id(7L).build();
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .billingAddressId(4L).build();
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(billingAddressService.resolveDefaultSnapshot(7L)).thenReturn(snapshot);
        when(paymentRequestMapper.buildCardVerificationConversationId(7L)).thenReturn("qr-card-verification-7-abc");
        PaymentCardVerificationRequest mappedRequest = PaymentCardVerificationRequest.builder().build();
        when(paymentRequestMapper.toCardVerificationRequest(
                eq(user), eq(snapshot), anyString(), eq(appProperties), eq("qr-card-verification-7-abc")
        )).thenReturn(mappedRequest);
        PaymentCheckoutFormResponse gatewayResponse = new PaymentCheckoutFormResponse();
        gatewayResponse.setConversationId("qr-card-verification-7-abc");
        gatewayResponse.setToken("token-1");
        gatewayResponse.setPaymentPageUrl("https://www.paytr.com/odeme/guvenli/token-1");
        gatewayResponse.setCheckoutFormContent("<script>...</script>");
        when(paymentServiceClient.initiateCardVerification(7L, mappedRequest)).thenReturn(gatewayResponse);

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.5");

        BillingPaymentDtos.CardVerificationInit result = controller.initiateCardVerification(httpRequest);

        assertThat(result.conversationId()).isEqualTo("qr-card-verification-7-abc");
        assertThat(result.token()).isEqualTo("token-1");
        assertThat(result.paymentPageUrl()).isEqualTo("https://www.paytr.com/odeme/guvenli/token-1");
        assertThat(result.checkoutFormContent()).isEqualTo("<script>...</script>");
        verify(paymentServiceClient).initiateCardVerification(7L, mappedRequest);
    }

    @Test
    void initiateCardVerification_whenForwardedForHeaderPresent_thenUseFirstIp() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        BillingAddressService billingAddressService = mock(BillingAddressService.class);
        PaymentRequestMapper paymentRequestMapper = mock(PaymentRequestMapper.class);
        AppProperties appProperties = new AppProperties();
        BillingPaymentController controller = new BillingPaymentController(
                paymentServiceClient, securityUtils, billingAddressService, paymentRequestMapper, appProperties
        );

        User user = User.builder().id(7L).build();
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL).build();
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(billingAddressService.resolveDefaultSnapshot(7L)).thenReturn(snapshot);
        when(paymentRequestMapper.buildCardVerificationConversationId(7L)).thenReturn("conv");
        when(paymentRequestMapper.toCardVerificationRequest(any(), any(), anyString(), any(), anyString()))
                .thenReturn(PaymentCardVerificationRequest.builder().build());
        when(paymentServiceClient.initiateCardVerification(eq(7L), any())).thenReturn(new PaymentCheckoutFormResponse());

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.1");

        controller.initiateCardVerification(httpRequest);

        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentRequestMapper).toCardVerificationRequest(
                eq(user), eq(snapshot), ipCaptor.capture(), eq(appProperties), eq("conv")
        );
        assertThat(ipCaptor.getValue()).isEqualTo("203.0.113.9");
    }

    @Test
    void cardVerificationStatus_whenAuthenticated_thenDelegateWithCurrentUser() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        BillingAddressService billingAddressService = mock(BillingAddressService.class);
        PaymentRequestMapper paymentRequestMapper = mock(PaymentRequestMapper.class);
        AppProperties appProperties = new AppProperties();
        BillingPaymentController controller = new BillingPaymentController(
                paymentServiceClient, securityUtils, billingAddressService, paymentRequestMapper, appProperties
        );

        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(7L).build());
        BillingPaymentDtos.RefundablePayment payment = new BillingPaymentDtos.RefundablePayment(
                "conv", "pay-1", "txn-1", "REFUNDED",
                java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO
        );
        when(paymentServiceClient.getCardVerificationStatus(7L, "conv")).thenReturn(payment);

        BillingPaymentDtos.RefundablePayment result = controller.cardVerificationStatus("conv");

        assertThat(result).isEqualTo(payment);
        assertThat(result.isCardVerificationComplete()).isTrue();
    }
}
