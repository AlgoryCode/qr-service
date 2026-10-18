package com.ael.algoryqrservice.qrservice.client;

import com.ael.algoryqrservice.paymentservice.application.dto.CheckoutFormResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.RefundResultDto;
import com.ael.algoryqrservice.paymentservice.application.service.CardStorageService;
import com.ael.algoryqrservice.paymentservice.application.service.PaymentMethodService;
import com.ael.algoryqrservice.paymentservice.application.service.PaymentService;
import com.ael.algoryqrservice.paymentservice.application.service.SubscriptionService;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InstallmentOptions;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentType;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.StoreCardCommand;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentGatewayPort;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.PaymentMethodResponse;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.SubscriptionResponse;
import com.ael.algoryqrservice.paymentservice.presentation.mapper.PaymentPresentationMapper;
import com.ael.algoryqrservice.qrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentCardStorageSessionResponse;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentCardVerificationRequest;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentThreeDsResponse;
import com.ael.algoryqrservice.qrservice.client.payment.PaymentBillingResponseMapper;
import com.ael.algoryqrservice.qrservice.client.payment.QrPaymentClientMapper;
import com.ael.algoryqrservice.qrservice.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class PaymentServiceClient {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final PaymentMethodService paymentMethodService;
    private final CardStorageService cardStorageService;
    private final PaymentPresentationMapper paymentPresentationMapper;
    private final QrPaymentClientMapper qrPaymentClientMapper;
    private final PaymentBillingResponseMapper billingResponseMapper;
    private final PaymentGatewayPort paymentGatewayPort;

    public PaymentServiceClient(
            PaymentService paymentService,
            SubscriptionService subscriptionService,
            PaymentMethodService paymentMethodService,
            CardStorageService cardStorageService,
            PaymentPresentationMapper paymentPresentationMapper,
            QrPaymentClientMapper qrPaymentClientMapper,
            PaymentBillingResponseMapper billingResponseMapper,
            @Qualifier("paytrPaymentGateway") PaymentGatewayPort paymentGatewayPort
    ) {
        this.paymentService = paymentService;
        this.subscriptionService = subscriptionService;
        this.paymentMethodService = paymentMethodService;
        this.cardStorageService = cardStorageService;
        this.paymentPresentationMapper = paymentPresentationMapper;
        this.qrPaymentClientMapper = qrPaymentClientMapper;
        this.billingResponseMapper = billingResponseMapper;
        this.paymentGatewayPort = paymentGatewayPort;
    }

    public PaymentThreeDsResponse initializeThreeDsPayment(PaymentThreeDsRequest request) {
        return toThreeDsResponse(pay(request));
    }

    public PaymentThreeDsResponse createDirectPayment(PaymentThreeDsRequest request) {
        return toThreeDsResponse(pay(request));
    }

    public BillingPaymentDtos.StoredCardCharge chargeStoredCard(Long merchantId, PaymentThreeDsRequest request) {
        PaymentResultDto result = pay(merchantId, request);
        return billingResponseMapper.toStoredCardCharge(result);
    }

    public PaymentCheckoutFormResponse initializeCheckoutForm(Long merchantId, PaymentCheckoutFormRequest request) {
        requireMerchantId(merchantId);
        InitializeCheckoutFormCommand command = qrPaymentClientMapper.toInitializeCheckoutFormCommand(merchantId, request);
        CheckoutFormResultDto result = paymentService.initializeCheckoutForm(command);
        PaymentCheckoutFormResponse response = new PaymentCheckoutFormResponse();
        response.setConversationId(result.getConversationId());
        response.setToken(result.getToken());
        response.setPaymentPageUrl(result.getPaymentPageUrl());
        response.setCheckoutFormContent(result.getCheckoutFormContent());
        return response;
    }

    public PaymentCardStorageSessionResponse initiateCardVerification(
            Long merchantId,
            PaymentCardVerificationRequest request
    ) {
        requireMerchantId(merchantId);
        StoreCardCommand command = paymentPresentationMapper.toStoreCardCommand(
                String.valueOf(merchantId),
                qrPaymentClientMapper.toCardVerificationInitRequest(request)
        );
        var session = cardStorageService.initiate(command);
        PaymentCardStorageSessionResponse response = new PaymentCardStorageSessionResponse();
        response.setConversationId(session.conversationId());
        response.setActionUrl(session.actionUrl());
        response.setFields(session.fields());
        return response;
    }

    public BillingPaymentDtos.RefundablePayment getCardVerificationStatus(Long merchantId, String conversationId) {
        return getRefundablePayment(merchantId, conversationId);
    }

    public List<BillingPaymentDtos.PaymentMethod> getPaymentMethods(Long merchantId) {
        requireMerchantId(merchantId);
        return paymentMethodService.list(String.valueOf(merchantId), PageRequest.of(0, 50)).stream()
                .map(billingResponseMapper::toPaymentMethod)
                .toList();
    }

    public BillingPaymentDtos.PaymentMethod createPaymentMethod(
            Long merchantId,
            String email,
            String alias,
            String cardHolderName,
            String cardNumber,
            String expireMonth,
            String expireYear
    ) {
        requireMerchantId(merchantId);
        PaymentMethodResponse response = paymentMethodService.create(
                String.valueOf(merchantId),
                qrPaymentClientMapper.toCreatePaymentMethodRequest(
                        email,
                        alias,
                        cardHolderName,
                        cardNumber,
                        expireMonth,
                        expireYear
                )
        );
        return billingResponseMapper.toPaymentMethod(response);
    }

    public void deletePaymentMethod(Long merchantId, String paymentMethodId) {
        requireMerchantId(merchantId);
        paymentMethodService.delete(String.valueOf(merchantId), Long.valueOf(paymentMethodId));
    }

    public BillingPaymentDtos.InstallmentOptions getInstallmentOptions(
            BigDecimal amount,
            String currency,
            String binNumber
    ) {
        List<InstallmentOptions> providers = paymentGatewayPort.retrieveInstallmentOptions(binNumber, amount, currency);
        List<BillingPaymentDtos.InstallmentOption> options = providers.stream()
                .flatMap(provider -> provider.options().stream())
                .map(billingResponseMapper::toInstallmentOption)
                .toList();
        return new BillingPaymentDtos.InstallmentOptions(options);
    }

    public List<BillingPaymentDtos.Subscription> getSubscriptions(Long merchantId) {
        return Collections.emptyList();
    }

    public BillingPaymentDtos.Subscription bootstrapSubscription(
            Long merchantId,
            String serviceName,
            String sourceReferenceId,
            String conversationId,
            BigDecimal amount,
            String currency,
            Integer billingIntervalMonths,
            Long paymentMethodId,
            LocalDateTime nextChargeAt,
            Map<String, Object> sourceMetadata
    ) {
        return bootstrapSubscription(
                merchantId,
                serviceName,
                sourceReferenceId,
                conversationId,
                amount,
                currency,
                billingIntervalMonths,
                paymentMethodId,
                nextChargeAt,
                sourceMetadata,
                null,
                null,
                null
        );
    }

    public BillingPaymentDtos.Subscription bootstrapSubscription(
            Long merchantId,
            String serviceName,
            String sourceReferenceId,
            String conversationId,
            BigDecimal amount,
            String currency,
            Integer billingIntervalMonths,
            Long paymentMethodId,
            LocalDateTime nextChargeAt,
            Map<String, Object> sourceMetadata,
            Object buyer,
            Object shippingAddress,
            Object billingAddress
    ) {
        requireMerchantId(merchantId);
        SubscriptionResponse response = subscriptionService.bootstrap(
                String.valueOf(merchantId),
                serviceName,
                sourceReferenceId,
                conversationId,
                amount,
                currency,
                billingIntervalMonths,
                paymentMethodId,
                nextChargeAt,
                sourceMetadata,
                null,
                null,
                null
        );
        return billingResponseMapper.toSubscription(response);
    }

    public BillingPaymentDtos.Subscription cancelSubscription(Long merchantId, String subscriptionId) {
        requireMerchantId(merchantId);
        return billingResponseMapper.toSubscription(
                subscriptionService.cancel(String.valueOf(merchantId), subscriptionId)
        );
    }

    public BillingPaymentDtos.Subscription cancelSubscriptionAtPeriodEnd(Long merchantId, String subscriptionId) {
        throw new PaymentServiceException("Abonelik donem sonu iptali monolit icinde desteklenmiyor");
    }

    public BillingPaymentDtos.Subscription resumeSubscription(Long merchantId, String subscriptionId) {
        throw new PaymentServiceException("Abonelik yenileme geri alma monolit icinde desteklenmiyor");
    }

    public Optional<BillingPaymentDtos.RefundablePayment> findPayment(Long merchantId, String conversationId) {
        try {
            return Optional.of(getRefundablePayment(merchantId, conversationId));
        } catch (PaymentServiceException exception) {
            return Optional.empty();
        }
    }

    public BillingPaymentDtos.PaymentDetail getPaymentDetail(String conversationId) {
        PaymentResultDto result = paymentService.retrievePayment(conversationId);
        return billingResponseMapper.toPaymentDetail(result);
    }

    public BillingPaymentDtos.PaymentPage searchPayments(
            String query,
            String status,
            String paymentType,
            String paymentStyle,
            String accountId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Boolean verificationOnly,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PaymentSearchQuery searchQuery = PaymentSearchQuery.builder()
                .query(query)
                .status(parseEnum(PaymentStatus.class, status))
                .paymentType(parseEnum(PaymentType.class, paymentType))
                .paymentStyle(parseEnum(PaymentStyle.class, paymentStyle))
                .accountId(accountId)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .verificationOnly(verificationOnly)
                .build();
        Page<PaymentResultDto> results = paymentService.searchPayments(
                searchQuery,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<BillingPaymentDtos.PaymentDetail> content = results.stream()
                .map(billingResponseMapper::toPaymentDetail)
                .toList();
        return new BillingPaymentDtos.PaymentPage(
                content,
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.hasNext()
        );
    }

    public BillingPaymentDtos.RefundablePayment getRefundablePayment(Long merchantId, String conversationId) {
        PaymentResultDto result = paymentService.retrievePayment(conversationId);
        return billingResponseMapper.toRefundablePayment(result);
    }

    public BillingPaymentDtos.RefundResult refundPayment(
            Long merchantId,
            String conversationId,
            BigDecimal amount,
            String clientIp
    ) {
        RefundPaymentCommand command = qrPaymentClientMapper.toRefundCommand(conversationId, amount, clientIp);
        RefundResultDto result = paymentService.refundPayment(command);
        return billingResponseMapper.toRefundResult(result);
    }

    private PaymentResultDto pay(PaymentThreeDsRequest request) {
        Long merchantId = extractMerchantIdFromMetadata(request);
        return pay(merchantId, request);
    }

    private PaymentResultDto pay(Long merchantId, PaymentThreeDsRequest request) {
        requireMerchantId(merchantId);
        CreatePaymentCommand command = qrPaymentClientMapper.toCreatePaymentCommand(merchantId, request);
        return paymentService.createDirectPayment(command);
    }

    private PaymentThreeDsResponse toThreeDsResponse(PaymentResultDto result) {
        PaymentThreeDsResponse response = new PaymentThreeDsResponse();
        response.setConversationId(result.getConversationId());
        response.setHtmlContent(null);
        return response;
    }

    private static void requireMerchantId(Long merchantId) {
        if (merchantId == null) {
            throw new PaymentServiceException("Ödeme için kullanıcı kimliği zorunludur");
        }
    }

    private static Long extractMerchantIdFromMetadata(PaymentThreeDsRequest request) {
        if (request == null || request.getSourceMetadata() == null) {
            return null;
        }
        Object raw = request.getSourceMetadata().get("merchantId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        return null;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, raw.trim());
    }
}
