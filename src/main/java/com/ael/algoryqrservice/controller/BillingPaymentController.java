package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.client.dto.PaymentCardVerificationRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.CreateSavedCardRequest;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.service.BillingAddressService;
import com.ael.algoryqrservice.service.PaymentRequestMapper;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingPaymentController {
    private final PaymentServiceClient paymentServiceClient;
    private final SecurityUtils securityUtils;
    private final BillingAddressService billingAddressService;
    private final PaymentRequestMapper paymentRequestMapper;
    private final AppProperties appProperties;

    @GetMapping("/payment-methods")
    public List<BillingPaymentDtos.PaymentMethod> paymentMethods() {
        return paymentServiceClient.getPaymentMethods(userId());
    }

    @PostMapping("/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingPaymentDtos.PaymentMethod createPaymentMethod(@Valid @RequestBody CreateSavedCardRequest request) {
        User user = securityUtils.getCurrentUser();
        return paymentServiceClient.createPaymentMethod(
                user.getId(),
                user.getEmail(),
                request.alias(),
                request.cardHolderName(),
                request.cardNumber(),
                request.expireMonth(),
                request.expireYear()
        );
    }

    @PostMapping("/payment-methods/verification")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingPaymentDtos.CardVerificationInit initiateCardVerification(HttpServletRequest httpServletRequest) {
        User user = securityUtils.getCurrentUser();
        BillingSnapshot billingSnapshot = billingAddressService.resolveDefaultSnapshot(user.getId());
        String conversationId = paymentRequestMapper.buildCardVerificationConversationId(user.getId());
        String clientIp = resolveClientIp(httpServletRequest);
        PaymentCardVerificationRequest request = paymentRequestMapper.toCardVerificationRequest(
                user, billingSnapshot, clientIp, appProperties, conversationId
        );
        PaymentCheckoutFormResponse response = paymentServiceClient.initiateCardVerification(user.getId(), request);
        return new BillingPaymentDtos.CardVerificationInit(
                response.getConversationId(),
                response.getToken(),
                response.getPaymentPageUrl(),
                response.getCheckoutFormContent()
        );
    }

    @GetMapping("/payment-methods/verification/{conversationId}")
    public BillingPaymentDtos.RefundablePayment cardVerificationStatus(@PathVariable String conversationId) {
        return paymentServiceClient.getCardVerificationStatus(userId(), conversationId);
    }

    @DeleteMapping("/payment-methods/{paymentMethodId}")
    public ResponseEntity<Void> deletePaymentMethod(@PathVariable String paymentMethodId) {
        paymentServiceClient.deletePaymentMethod(userId(), paymentMethodId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/installment-options")
    public BillingPaymentDtos.InstallmentOptions installmentOptions(
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "TRY") String currency,
            @RequestParam String binNumber
    ) {
        return paymentServiceClient.getInstallmentOptions(amount, currency, binNumber);
    }

    @GetMapping("/subscriptions")
    public List<BillingPaymentDtos.Subscription> subscriptions() {
        return paymentServiceClient.getSubscriptions(userId());
    }

    @PostMapping("/subscriptions/{subscriptionId}/cancel")
    public BillingPaymentDtos.Subscription cancelSubscription(@PathVariable String subscriptionId) {
        throw new BadRequestException(
                "Use POST /purchases/{id}/cancel-at-period-end or /purchases/{id}/cancel-with-refund"
        );
    }

    private Long userId() {
        return securityUtils.getCurrentUser().getId();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
