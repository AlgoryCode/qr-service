package com.ael.algoryqrservice.paymentservice.application.service;

import com.ael.algoryqrservice.paymentservice.application.dto.CheckoutFormResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.RefundResultDto;
import com.ael.algoryqrservice.paymentservice.application.mapper.PaymentApplicationMapper;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentNotFoundException;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.BuyerInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCardInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCompletedEvent;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentGatewayResult;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentProvider;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentType;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.paymentservice.domain.model.PaytrNotificationCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentEventPublisherPort;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentGatewayPort;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentNotificationPublisherPort;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentRepositoryPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentGatewayProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaytrProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.ServiceNameValidator;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.SourceMetadataConverter;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaytrHashService;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaytrMerchantOid;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaymentGatewayRegistry;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.mapper.PaytrRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String GATEWAY_SUCCESS_STATUS = "success";
    private static final String GATEWAY_SUCCESS_PAYMENT_STATUS = "SUCCESS";
    private static final String GATEWAY_FAILURE_PAYMENT_STATUS = "FAILURE";
    private static final String SOURCE_METADATA_PURCHASE_ID_KEY = "purchaseId";
    private static final String CARD_VERIFICATION_DEFAULT_LOCALE = "tr";
    private static final String CARD_VERIFICATION_DEFAULT_IP = "127.0.0.1";
    private static final String PAYTR_TIMEOUT_ERROR = "PAYTR_TIMEOUT";

    private final PaymentGatewayRegistry paymentGatewayRegistry;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentNotificationPublisherPort paymentNotificationPublisherPort;
    private final PaymentEventPublisherPort paymentEventPublisherPort;
    private final PaymentApplicationMapper paymentApplicationMapper;
    private final ServiceNameValidator serviceNameValidator;
    private final SourceMetadataConverter sourceMetadataConverter;
    private final PaymentGatewayProperties paymentGatewayProperties;
    private final PaytrProperties paytrProperties;
    private final PaytrHashService paytrHashService;
    private final PaytrRequestMapper paytrRequestMapper;
    private final PaymentMethodService paymentMethodService;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional
    public PaymentResultDto createDirectPayment(CreatePaymentCommand command) {
        validateServiceMetadata(command);
        command.setConversationId(PaytrMerchantOid.sanitize(command.getConversationId()));
        Optional<PaymentTransaction> existingTransaction = findExistingTransaction(command.getConversationId());
        if (existingTransaction.isPresent()) {
            return paymentApplicationMapper.toPaymentResultDto(existingTransaction.get());
        }
        prepareStoredCardCommand(command);
        PaymentProvider provider = resolveProvider(command.getProvider());
        command.setProvider(provider);
        PaymentTransaction transaction = buildInitiatedTransaction(command, PaymentType.DIRECT);
        paymentRepositoryPort.save(transaction);

        PaymentGatewayResult gatewayResult = paymentGatewayRegistry.resolve(provider).createDirectPayment(command);
        if (isTerminalPaymentOutcome(gatewayResult)) {
            updateTransactionFromGatewayResult(transaction, gatewayResult, true);
            if (transaction.getStatus() == PaymentStatus.SUCCESS) {
                if (transaction.getPaymentStyle() == PaymentStyle.SUBSCRIPTION) {
                    subscriptionService.activate(transaction, gatewayResult);
                }
                publishPaymentSuccessNotification(command, transaction);
                publishPaymentEvent(transaction, true);
            } else if (transaction.getStatus() == PaymentStatus.FAILURE) {
                if (isSubscriptionRenewal(command)) {
                    log.warn("Suppressing intermediate renewal failure event until all cards are tried. paymentId={}",
                            transaction.getConversationId());
                } else {
                    publishPaymentEvent(transaction, false);
                }
            }
        }

        log.info(
                "Stored-card payment submitted. merchantId={} paymentId={} status={}",
                transaction.getAccountId(),
                transaction.getConversationId(),
                transaction.getStatus()
        );
        return paymentApplicationMapper.toPaymentResultDto(transaction);
    }

    private boolean isSubscriptionRenewal(CreatePaymentCommand command) {
        return command.getPaymentStyle() == PaymentStyle.SUBSCRIPTION
                && command.getSubscriptionId() != null
                && !command.getSubscriptionId().isBlank()
                && command.getBillingCycleNumber() != null;
    }

    @Override
    @Transactional
    public CheckoutFormResultDto initializeCheckoutForm(InitializeCheckoutFormCommand command) {
        serviceNameValidator.validate(command.getServiceName());
        command.setConversationId(PaytrMerchantOid.sanitize(command.getConversationId()));
        validateConversationIdUniqueness(command.getConversationId());
        PaymentStyle style = command.getPaymentStyle() == null ? PaymentStyle.ONE_TIME : command.getPaymentStyle();
        command.setPaymentStyle(style);
        if (style == PaymentStyle.SUBSCRIPTION) {
            subscriptionService.prepareCheckoutFormSubscription(command);
        }
        PaymentProvider requestedProvider = command.getProvider();
        PaymentProvider provider = resolveProvider(requestedProvider);
        command.setProvider(provider);
        log.info(
                "Checkout form started. merchantId={} paymentId={} provider={}",
                command.getAccountId(),
                command.getConversationId(),
                provider
        );

        PaymentGatewayResult gatewayResult = paymentGatewayRegistry.resolve(provider).initializeCheckoutForm(command);
        validateGatewayResponse(gatewayResult);
        requireCheckoutPayload(gatewayResult);

        PaymentTransaction transaction = buildInitiatedCheckoutTransaction(command, gatewayResult);
        paymentRepositoryPort.save(transaction);

        log.info(
                "Checkout form created. merchantId={} paymentId={} style={}",
                command.getAccountId(),
                command.getConversationId(),
                command.getPaymentStyle()
        );
        return paymentApplicationMapper.toCheckoutFormResultDto(gatewayResult);
    }

    @Override
    @Transactional
    public PaymentResultDto retrievePayment(String conversationId) {
        PaymentTransaction transaction = requireTransaction(conversationId);
        if (transaction.getStatus() == PaymentStatus.INITIATED) {
            reconcileInitiatedTransaction(transaction);
        }
        return paymentApplicationMapper.toPaymentResultDto(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResultDto> searchPayments(PaymentSearchQuery query, Pageable pageable) {
        return paymentRepositoryPort.search(query, pageable)
                .map(paymentApplicationMapper::toPaymentResultDto);
    }

    @Override
    @Transactional
    public String handlePaytrNotification(PaytrNotificationCommand command) {
        log.info(
                "Card verification callback step=RECEIVED conversationId={} status={} "
                        + "utokenPresent={} ctokenPresent={} maskedPanPresent={}",
                command.merchantOid(),
                command.status(),
                !isBlank(command.utoken()),
                !isBlank(command.ctoken()),
                !isBlank(command.maskedPan())
        );
        if (command.merchantOid() == null || command.merchantOid().isBlank()) {
            throw new PaymentProcessingException("PAYTR_MERCHANT_OID_MISSING", "PayTR merchant_oid eksik");
        }
        boolean hashValid = paytrHashService.matchesNotification(
                command.merchantOid(),
                command.status(),
                command.totalAmount(),
                command.hash()
        );
        if (!hashValid) {
            log.warn("Payment notification rejected. paymentId={} reason=INVALID_HASH", command.merchantOid());
            throw new PaymentProcessingException("INVALID_PAYTR_HASH", "PayTR bildirim hash dogrulanamadi");
        }
        log.info("Card verification callback step=HASH_VALIDATED conversationId={}", command.merchantOid());

        Optional<PaymentTransaction> existing = findExistingTransaction(command.merchantOid());
        if (existing.isEmpty()) {
            log.warn("Payment notification ignored. paymentId={} reason=UNKNOWN_PAYMENT", command.merchantOid());
            return "OK";
        }
        PaymentTransaction transaction = existing.get();
        log.info(
                "Card verification callback step=TRANSACTION_FOUND conversationId={} accountId={} "
                        + "currentStatus={} verificationOnly={}",
                transaction.getConversationId(),
                transaction.getAccountId(),
                transaction.getStatus(),
                transaction.isVerificationOnly()
        );
        if (transaction.getStatus() != PaymentStatus.INITIATED) {
            log.info(
                    "Payment notification ignored. merchantId={} paymentId={} reason=ALREADY_FINAL status={}",
                    transaction.getAccountId(),
                    transaction.getConversationId(),
                    transaction.getStatus()
            );
            return "OK";
        }

        PaymentGatewayResult gatewayResult = paytrRequestMapper.toNotificationResult(command, true);
        log.info(
                "Card verification callback step=PAYTR_RESULT_MAPPED conversationId={} paymentStatus={} "
                        + "cardUserKeyPresent={} cardTokenPresent={} lastFourPresent={}",
                transaction.getConversationId(),
                gatewayResult.getPaymentStatus(),
                !isBlank(gatewayResult.getCardUserKey()),
                !isBlank(gatewayResult.getCardToken()),
                !isBlank(gatewayResult.getLastFourDigits())
        );
        updateTransactionFromGatewayResult(transaction, gatewayResult, true);

        if (transaction.getStatus() == PaymentStatus.INITIATED) {
            log.info(
                    "Payment notification pending. merchantId={} paymentId={}",
                    transaction.getAccountId(),
                    transaction.getConversationId()
            );
            return "OK";
        }

        boolean success = transaction.getStatus() == PaymentStatus.SUCCESS;
        if (success) {
            log.info("Card verification callback step=PAYMENT_SUCCESS conversationId={}",
                    transaction.getConversationId());
            boolean registered = registerCardIfEligible(transaction, gatewayResult);
            if (transaction.isVerificationOnly()) {
                if (!registered) {
                    transaction.setErrorCode("CARD_TOKEN_MISSING");
                    transaction.setErrorMessage("Kart PayTR tarafinda kaydedilmedi");
                    transaction.setUpdatedAt(LocalDateTime.now());
                    paymentRepositoryPort.save(transaction);
                    log.warn(
                            "Card verification payment succeeded but card was not saved. merchantId={} paymentId={} reason=CARD_TOKEN_MISSING",
                            transaction.getAccountId(),
                            transaction.getConversationId()
                    );
                } else {
                    log.info(
                            "Card verification payment succeeded and card saved. merchantId={} paymentId={}",
                            transaction.getAccountId(),
                            transaction.getConversationId()
                    );
                }
                return "OK";
            }
            log.info(
                    "Payment succeeded. merchantId={} paymentId={}",
                    transaction.getAccountId(),
                    transaction.getConversationId()
            );
        } else {
            log.warn(
                    "Payment failed. merchantId={} paymentId={} reason={}",
                    transaction.getAccountId(),
                    transaction.getConversationId(),
                    firstNonBlank(transaction.getErrorCode(), transaction.getStatus().name())
            );
        }
        if (success && transaction.getPaymentStyle() == PaymentStyle.SUBSCRIPTION) {
            subscriptionService.activate(transaction, gatewayResult);
        }
        publishPaymentEvent(transaction, success);
        if (success) {
            publishPaymentSuccessNotification(transaction);
        }
        return "OK";
    }

    @Override
    @Transactional
    public void refundPendingVerificationCharges() {
        LocalDateTime updatedBefore = LocalDateTime.now().minusSeconds(30);
        LocalDateTime createdAfter = LocalDateTime.now().minusDays(7);
        var pending = paymentRepositoryPort.findVerificationChargesPendingRefund(
                PaymentStatus.SUCCESS,
                updatedBefore,
                createdAfter,
                25
        );
        if (pending.isEmpty()) {
            return;
        }
        log.info("Card verification refund sweep started. count={}", pending.size());
        for (PaymentTransaction transaction : pending) {
            reverseVerificationCharge(transaction, PaymentGatewayResult.builder()
                    .conversationId(transaction.getConversationId())
                    .paymentId(transaction.getPaymentId())
                    .paymentTransactionId(firstNonBlank(
                            transaction.getPaymentTransactionId(),
                            transaction.getConversationId()
                    ))
                    .paidPrice(transaction.getPaidPrice())
                    .signatureValid(true)
                    .build());
        }
    }

    private void reconcileInitiatedTransaction(PaymentTransaction transaction) {
        PaymentGatewayResult gatewayResult = paymentGatewayRegistry.resolve(resolveProvider(transaction.getProvider()))
                .retrievePayment(transaction.getConversationId(), transaction.getPaymentId());
        if (isTerminalPaymentOutcome(gatewayResult)) {
            updateTransactionFromGatewayResult(transaction, gatewayResult, true);
            if (transaction.getStatus() != PaymentStatus.INITIATED) {
                boolean success = transaction.getStatus() == PaymentStatus.SUCCESS;
                if (success && transaction.getPaymentStyle() == PaymentStyle.SUBSCRIPTION) {
                    subscriptionService.activate(transaction, gatewayResult);
                }
                publishPaymentEvent(transaction, success);
                if (success) {
                    publishPaymentSuccessNotification(transaction);
                }
            }
            return;
        }
        if (isCheckoutExpired(transaction)) {
            markFailure(transaction, PAYTR_TIMEOUT_ERROR, "PayTR odeme suresi doldu");
            publishPaymentEvent(transaction, false);
            log.info("Payment expired. merchantId={} paymentId={} reason=TIMEOUT",
                    transaction.getAccountId(),
                    transaction.getConversationId());
        }
    }

    private boolean isCheckoutExpired(PaymentTransaction transaction) {
        if (transaction.getCreatedAt() == null) {
            return false;
        }
        return transaction.getCreatedAt()
                .plusMinutes(paytrProperties.timeoutLimitMinutes())
                .isBefore(LocalDateTime.now());
    }

    private void reverseVerificationCharge(PaymentTransaction transaction, PaymentGatewayResult gatewayResult) {
        boolean reversed = tryCancelVerificationPayment(transaction);
        if (!reversed) {
            reversed = tryRefundVerificationPayment(transaction, gatewayResult);
        }
        if (reversed) {
            transaction.setRefundedAmount(transaction.getPaidPrice());
            transaction.setStatus(PaymentStatus.REFUNDED);
            transaction.setUpdatedAt(LocalDateTime.now());
            paymentRepositoryPort.save(transaction);
            log.info(
                    "Card verification refund succeeded. merchantId={} paymentId={}",
                    transaction.getAccountId(),
                    transaction.getConversationId()
            );
        } else {
            log.error(
                    "Card verification refund failed. merchantId={} paymentId={} reason=MANUAL_REFUND_REQUIRED",
                    transaction.getAccountId(),
                    transaction.getConversationId()
            );
        }
    }

    private boolean tryCancelVerificationPayment(PaymentTransaction transaction) {
        return false;
    }

    private boolean tryRefundVerificationPayment(PaymentTransaction transaction, PaymentGatewayResult gatewayResult) {
        String paymentTransactionId = firstNonBlank(
                gatewayResult == null ? null : gatewayResult.getPaymentTransactionId(),
                transaction.getPaymentTransactionId(),
                transaction.getConversationId()
        );
        if (paymentTransactionId == null) {
            return false;
        }
        try {
            PaymentGatewayResult result = paymentGatewayRegistry.resolve(resolveProvider(transaction.getProvider()))
                    .refundPayment(RefundPaymentCommand.builder()
                    .conversationId(transaction.getConversationId())
                    .paymentTransactionId(paymentTransactionId)
                    .locale(CARD_VERIFICATION_DEFAULT_LOCALE)
                    .price(transaction.getPaidPrice())
                    .currency(transaction.getCurrency())
                    .ip(CARD_VERIFICATION_DEFAULT_IP)
                    .build());
            return GATEWAY_SUCCESS_STATUS.equalsIgnoreCase(result.getStatus());

        } catch (Exception exception) {
            log.error(
                    "Card verification refund failed. merchantId={} paymentId={} reason=GATEWAY_ERROR",
                    transaction.getAccountId(),
                    transaction.getConversationId()
            );
            return false;
        }
    }

    private boolean registerCardIfEligible(PaymentTransaction transaction, PaymentGatewayResult gatewayResult) {
        log.info(
                "Card registration step=START conversationId={} accountId={} cardUserKeyPresent={} cardTokenPresent={}",
                transaction.getConversationId(),
                transaction.getAccountId(),
                !isBlank(gatewayResult.getCardUserKey()),
                !isBlank(gatewayResult.getCardToken())
        );
        if (transaction.getAccountId() == null || transaction.getAccountId().isBlank()) {
            log.warn(
                    "Card save skipped. paymentId={} reason=USER_ID_MISSING",
                    transaction.getConversationId()
            );
            return false;
        }
        enrichCardTokensFromPaytr(transaction, gatewayResult);
        log.info(
                "Card registration step=TOKEN_ENRICHED conversationId={} cardUserKeyPresent={} cardTokenPresent={} lastFourPresent={}",
                transaction.getConversationId(),
                !isBlank(gatewayResult.getCardUserKey()),
                !isBlank(gatewayResult.getCardToken()),
                !isBlank(gatewayResult.getLastFourDigits())
        );
        if (gatewayResult.getCardUserKey() == null || gatewayResult.getCardToken() == null) {
            log.warn(
                    "Card save skipped. merchantId={} paymentId={} reason=CARD_TOKEN_MISSING",
                    transaction.getAccountId(),
                    transaction.getConversationId()
            );
            return false;
        }
        try {
            paymentMethodService.saveGatewayToken(
                    transaction.getAccountId(),
                    "Kayıtlı kart",
                    gatewayResult.getCardUserKey(),
                    gatewayResult.getCardToken(),
                    gatewayResult.getLastFourDigits()
            );
            log.info(
                    "Card registration step=DB_SAVED merchantId={} paymentId={}",
                    transaction.getAccountId(),
                    transaction.getConversationId()
            );
            return true;
        } catch (Exception exception) {
            log.error(
                    "Card registration step=DB_FAILED merchantId={} paymentId={} reason={} errorType={}",
                    transaction.getAccountId(),
                    transaction.getConversationId(),
                    exception.getMessage(),
                    exception.getClass().getSimpleName(),
                    exception
            );
            return false;
        }
    }

    @Override
    @Transactional
    public RefundResultDto refundPayment(RefundPaymentCommand command) {
        PaymentTransaction transaction = requireTransaction(command.getConversationId());

        if (transaction.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentProcessingException(
                    "REFUND_NOT_ALLOWED",
                    "Sadece basarili odemeler icin iade yapilabilir. Mevcut durum: " + transaction.getStatus()
            );
        }

        String paymentTransactionId = transaction.getPaymentTransactionId();
        if ((paymentTransactionId == null || paymentTransactionId.isBlank())
                && transaction.getProvider() == PaymentProvider.PAYTR) {
            paymentTransactionId = transaction.getConversationId();
            transaction.setPaymentTransactionId(paymentTransactionId);
        }
        if (paymentTransactionId == null || paymentTransactionId.isBlank()) {
            throw new PaymentProcessingException(
                    "REFUND_TRANSACTION_ID_MISSING",
                    "Bu odeme icin gateway islem kimligi bulunamadi, iade yapilamaz"
            );
        }

        BigDecimal alreadyRefunded = transaction.getRefundedAmount() == null
                ? BigDecimal.ZERO
                : transaction.getRefundedAmount();
        BigDecimal remaining = transaction.getPaidPrice().subtract(alreadyRefunded);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException(
                    "REFUND_ALREADY_COMPLETED",
                    "Bu odeme icin iade edilebilecek tutar kalmadi"
            );
        }
        if (command.getPrice().compareTo(remaining) > 0) {
            throw new PaymentProcessingException(
                    "REFUND_AMOUNT_EXCEEDS_REMAINING",
                    "Iade tutari kalan iade edilebilir tutari asamaz. Kalan: " + remaining
            );
        }

        command.setPaymentTransactionId(paymentTransactionId);
        command.setCurrency(transaction.getCurrency());

        PaymentGatewayPort gateway = paymentGatewayRegistry.resolve(resolveProvider(transaction.getProvider()));
        PaymentGatewayResult gatewayResult;
        try {
            gatewayResult = gateway.refundPayment(command);
        } catch (RuntimeException exception) {
            log.error(
                    "Refund failed. merchantId={} paymentId={} reason=GATEWAY_ERROR",
                    transaction.getAccountId(),
                    command.getConversationId()
            );
            throw exception;
        }
        validateGatewayResponse(gatewayResult);
        if (!gatewayResult.isSignatureValid()) {
            log.error(
                    "Refund failed. merchantId={} paymentId={} reason=INVALID_SIGNATURE",
                    transaction.getAccountId(),
                    command.getConversationId()
            );
            throw new PaymentProcessingException(
                    "INVALID_GATEWAY_SIGNATURE",
                    "Gateway iade yaniti imzasi dogrulanamadi"
            );
        }

        BigDecimal newRefundedAmount = alreadyRefunded.add(command.getPrice());
        transaction.setRefundedAmount(newRefundedAmount);
        boolean fullyRefunded = newRefundedAmount.compareTo(transaction.getPaidPrice()) >= 0;
        if (fullyRefunded) {
            transaction.setStatus(PaymentStatus.REFUNDED);
        }
        transaction.setUpdatedAt(LocalDateTime.now());
        paymentRepositoryPort.save(transaction);
        if (fullyRefunded) {
            publishPaymentEvent(transaction, false);
        }

        log.info(
                "Payment refunded. merchantId={} paymentId={} fullyRefunded={} status={}",
                transaction.getAccountId(),
                command.getConversationId(),
                fullyRefunded,
                transaction.getStatus()
        );
        return paymentApplicationMapper.toRefundResultDto(gatewayResult, paymentTransactionId, newRefundedAmount);
    }

    private void validateServiceMetadata(CreatePaymentCommand command) {
        serviceNameValidator.validate(command.getServiceName());
    }

    private void prepareStoredCardCommand(CreatePaymentCommand command) {
        PaymentStyle style = resolvePaymentStyle(command);
        command.setPaymentStyle(style);
        validatePaymentStyle(command, style, false);
        if (command.getPaymentMethodId() != null) {
            if (command.getAccountId() == null || command.getAccountId().isBlank()) {
                throw new PaymentProcessingException("ACCOUNT_REQUIRED", "Account identifier is required");
            }
            command.setPaymentCard(paymentMethodService.resolveToken(
                    command.getAccountId(),
                    command.getPaymentMethodId()
            ));
        }
        PaymentCardInfo card = command.getPaymentCard();
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

    private void validatePaymentStyle(CreatePaymentCommand command, PaymentStyle style, boolean threeDs) {
        if (style == PaymentStyle.BANK_INSTALLMENT) {
            if (command.getBankInstallmentCount() == null || command.getBankInstallmentCount() < 2) {
                throw new PaymentProcessingException(
                        "INVALID_BANK_INSTALLMENT_COUNT",
                        "Bank installment count must be at least two"
                );
            }
            if (command.getSubscriptionCycleCount() != null || command.getBillingIntervalMonths() != null) {
                throw new PaymentProcessingException(
                        "MIXED_PAYMENT_STYLE",
                        "Bank installment and subscription fields cannot be combined"
                );
            }
            return;
        }
        if (style == PaymentStyle.SUBSCRIPTION) {
            if (command.getBankInstallmentCount() != null) {
                throw new PaymentProcessingException(
                        "MIXED_PAYMENT_STYLE",
                        "Subscription and bank installment fields cannot be combined"
                );
            }
            if (command.getBillingCycleNumber() == null && !threeDs) {
                throw new PaymentProcessingException(
                        "THREE_DS_REQUIRED",
                        "Initial subscription payment must use 3DS"
                );
            }
            return;
        }
        if (command.getBankInstallmentCount() != null
                || command.getSubscriptionCycleCount() != null
                || command.getBillingIntervalMonths() != null) {
            throw new PaymentProcessingException(
                    "UNEXPECTED_STYLE_FIELDS",
                    "ONE_TIME payments cannot include installment or subscription fields"
            );
        }
    }

    private PaymentStyle resolvePaymentStyle(CreatePaymentCommand command) {
        return command.getPaymentStyle() == null ? PaymentStyle.ONE_TIME : command.getPaymentStyle();
    }

    private void validateConversationIdUniqueness(String conversationId) {
        if (findExistingTransaction(conversationId).isPresent()) {
            throw new PaymentProcessingException(
                    "DUPLICATE_CONVERSATION_ID",
                    "Payment with conversationId already exists: " + conversationId
            );
        }
    }

    private PaymentTransaction requireTransaction(String conversationId) {
        return findExistingTransaction(conversationId)
                .orElseThrow(() -> new PaymentNotFoundException(conversationId));
    }

    private Optional<PaymentTransaction> findExistingTransaction(String conversationId) {
        Optional<PaymentTransaction> exact = paymentRepositoryPort.findByConversationId(conversationId);
        if (exact.isPresent()) {
            return exact;
        }
        String sanitized = PaytrMerchantOid.sanitize(conversationId);
        if (sanitized.equals(conversationId)) {
            return Optional.empty();
        }
        return paymentRepositoryPort.findByConversationId(sanitized);
    }

    private void validateGatewayResponse(PaymentGatewayResult gatewayResult) {
        if (!GATEWAY_SUCCESS_STATUS.equalsIgnoreCase(gatewayResult.getStatus())) {
            throw new PaymentProcessingException(
                    gatewayResult.getErrorCode(),
                    gatewayResult.getErrorMessage()
            );
        }
    }

    private void requireCheckoutPayload(PaymentGatewayResult gatewayResult) {
        if (gatewayResult.getToken() == null || gatewayResult.getToken().isBlank()) {
            throw new PaymentProcessingException(
                    "CHECKOUT_FORM_TOKEN_MISSING",
                    "Checkout form token dondurmedi"
            );
        }
    }

    private PaymentTransaction buildPaymentTransaction(
            CreatePaymentCommand command,
            PaymentGatewayResult gatewayResult,
            PaymentType paymentType
    ) {
        LocalDateTime now = LocalDateTime.now();
        return PaymentTransaction.builder()
                .conversationId(command.getConversationId())
                .provider(resolveProvider(command.getProvider()))
                .paymentId(gatewayResult.getPaymentId())
                .paymentTransactionId(gatewayResult.getPaymentTransactionId())
                .accountId(command.getAccountId())
                .basketId(command.getBasketId())
                .price(command.getPrice())
                .paidPrice(command.getPaidPrice())
                .refundedAmount(BigDecimal.ZERO)
                .currency(command.getCurrency())
                .status(PaymentStatus.SUCCESS)
                .paymentType(paymentType)
                .serviceName(command.getServiceName())
                .sourceReferenceId(command.getSourceReferenceId())
                .sourceMetadataJson(sourceMetadataConverter.toJson(command.getSourceMetadata()))
                .buyerEmail(resolveBuyerEmail(command.getBuyer()))
                .buyerName(resolveBuyerName(command.getBuyer()))
                .paymentStyle(resolvePaymentStyle(command))
                .bankInstallmentCount(command.getBankInstallmentCount())
                .subscriptionId(command.getSubscriptionId())
                .billingCycleNumber(command.getBillingCycleNumber())
                .periodStart(command.getPeriodStart())
                .periodEnd(command.getPeriodEnd())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private PaymentTransaction buildInitiatedTransaction(CreatePaymentCommand command, PaymentType paymentType) {
        LocalDateTime now = LocalDateTime.now();
        return PaymentTransaction.builder()
                .conversationId(command.getConversationId())
                .provider(resolveProvider(command.getProvider()))
                .accountId(command.getAccountId())
                .basketId(command.getBasketId())
                .price(command.getPrice())
                .paidPrice(command.getPaidPrice())
                .refundedAmount(BigDecimal.ZERO)
                .currency(command.getCurrency())
                .status(PaymentStatus.INITIATED)
                .paymentType(paymentType)
                .serviceName(command.getServiceName())
                .sourceReferenceId(command.getSourceReferenceId())
                .sourceMetadataJson(sourceMetadataConverter.toJson(command.getSourceMetadata()))
                .buyerEmail(resolveBuyerEmail(command.getBuyer()))
                .buyerName(resolveBuyerName(command.getBuyer()))
                .paymentStyle(resolvePaymentStyle(command))
                .bankInstallmentCount(command.getBankInstallmentCount())
                .subscriptionId(command.getSubscriptionId())
                .billingCycleNumber(command.getBillingCycleNumber())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private PaymentTransaction buildInitiatedCheckoutTransaction(
            InitializeCheckoutFormCommand command,
            PaymentGatewayResult gatewayResult
    ) {
        LocalDateTime now = LocalDateTime.now();
        return PaymentTransaction.builder()
                .conversationId(command.getConversationId())
                .provider(resolveProvider(command.getProvider()))
                .gatewayToken(gatewayResult.getToken())
                .accountId(command.getAccountId())
                .basketId(command.getBasketId())
                .price(command.getPrice())
                .paidPrice(command.getPaidPrice())
                .refundedAmount(BigDecimal.ZERO)
                .currency(command.getCurrency())
                .status(PaymentStatus.INITIATED)
                .paymentType(PaymentType.CHECKOUT_FORM)
                .serviceName(command.getServiceName())
                .sourceReferenceId(command.getSourceReferenceId())
                .sourceMetadataJson(sourceMetadataConverter.toJson(command.getSourceMetadata()))
                .buyerEmail(resolveBuyerEmail(command.getBuyer()))
                .buyerName(resolveBuyerName(command.getBuyer()))
                .paymentStyle(command.getPaymentStyle() == null ? PaymentStyle.ONE_TIME : command.getPaymentStyle())
                .bankInstallmentCount(resolveCheckoutInstallmentCount(command))
                .subscriptionId(command.getSubscriptionId())
                .billingCycleNumber(command.getBillingCycleNumber())
                .periodStart(command.getPeriodStart())
                .periodEnd(command.getPeriodEnd())
                .verificationOnly(command.isVerificationOnly())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void enrichCardTokensFromPaytr(PaymentTransaction transaction, PaymentGatewayResult gatewayResult) {
        if (gatewayResult.getCardToken() != null || gatewayResult.getCardUserKey() == null) {
            return;
        }
        try {
            var listed = paymentGatewayRegistry
                    .resolve(resolveProvider(transaction.getProvider()))
                    .listStoredCards(gatewayResult.getCardUserKey());
            if (listed.isEmpty()) {
                log.warn(
                        "Card token lookup empty. merchantId={} paymentId={}",
                        transaction.getAccountId(),
                        transaction.getConversationId()
                );
                return;
            }
            var first = listed.getFirst();
            gatewayResult.setCardToken(first.cardToken());
            if (gatewayResult.getLastFourDigits() == null || gatewayResult.getLastFourDigits().isBlank()) {
                gatewayResult.setLastFourDigits(first.lastFour());
            }
        } catch (Exception exception) {
            log.warn(
                    "Card token lookup failed. merchantId={} paymentId={} reason=GATEWAY_ERROR",
                    transaction.getAccountId(),
                    transaction.getConversationId()
            );
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void updateTransactionFromGatewayResult(
            PaymentTransaction transaction,
            PaymentGatewayResult gatewayResult,
            boolean enforceSignature
    ) {
        warnOnPaymentIdMismatch(transaction, gatewayResult.getPaymentId());
        if (gatewayResult.getPaymentId() != null) {
            transaction.setPaymentId(gatewayResult.getPaymentId());
        }

        if (!isTerminalPaymentOutcome(gatewayResult)) {
            return;
        }

        if (enforceSignature && gatewayResult.getErrorCode() == null && !gatewayResult.isSignatureValid()) {
            log.error("Gateway response signature invalid. conversationId={}", transaction.getConversationId());
            markFailure(transaction, "INVALID_GATEWAY_SIGNATURE", "gateway yanıt imzası doğrulanamadı");
            return;
        }

        if (enforceSignature && isAmountMismatch(transaction, gatewayResult)) {
            log.error(
                    "Amount mismatch. conversationId={}, expected={}, actual={}",
                    transaction.getConversationId(),
                    transaction.getPaidPrice(),
                    gatewayResult.getPaidPrice()
            );
            markFailure(transaction, "AMOUNT_MISMATCH", "Gateway tutarı transaction ile uyuşmuyor");
            return;
        }

        boolean isSuccess = gatewayResult.getPaymentStatus() != null
                ? GATEWAY_SUCCESS_PAYMENT_STATUS.equalsIgnoreCase(gatewayResult.getPaymentStatus())
                : GATEWAY_SUCCESS_STATUS.equalsIgnoreCase(gatewayResult.getStatus());

        if (isSuccess && gatewayResult.getPaymentTransactionId() != null) {
            transaction.setPaymentTransactionId(gatewayResult.getPaymentTransactionId());
        }
        if (isSuccess
                && gatewayResult.getInstallmentCount() != null
                && gatewayResult.getInstallmentCount() > 1
                && transaction.getPaymentStyle() != PaymentStyle.SUBSCRIPTION) {
            transaction.setBankInstallmentCount(gatewayResult.getInstallmentCount());
            transaction.setPaymentStyle(PaymentStyle.BANK_INSTALLMENT);
        }
        transaction.setStatus(isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILURE);
        transaction.setErrorCode(gatewayResult.getErrorCode());
        transaction.setErrorMessage(gatewayResult.getErrorMessage());
        transaction.setUpdatedAt(LocalDateTime.now());
        paymentRepositoryPort.save(transaction);
    }

    private boolean isTerminalPaymentOutcome(PaymentGatewayResult gatewayResult) {
        String paymentStatus = gatewayResult.getPaymentStatus();
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            if (GATEWAY_SUCCESS_PAYMENT_STATUS.equalsIgnoreCase(paymentStatus)) {
                return true;
            }
            if (GATEWAY_FAILURE_PAYMENT_STATUS.equalsIgnoreCase(paymentStatus)) {
                return hasCompletedPaymentAttempt(gatewayResult);
            }
            return false;
        }
        return GATEWAY_SUCCESS_STATUS.equalsIgnoreCase(gatewayResult.getStatus());
    }

    private boolean hasCompletedPaymentAttempt(PaymentGatewayResult gatewayResult) {
        return (gatewayResult.getPaymentId() != null && !gatewayResult.getPaymentId().isBlank())
                || gatewayResult.getPaidPrice() != null
                || (gatewayResult.getErrorCode() != null && !gatewayResult.getErrorCode().isBlank())
                || (gatewayResult.getErrorMessage() != null && !gatewayResult.getErrorMessage().isBlank());
    }

    private boolean isAmountMismatch(PaymentTransaction transaction, PaymentGatewayResult gatewayResult) {
        if (gatewayResult.getPaidPrice() == null || transaction.getPaidPrice() == null) {
            return false;
        }
        if (transaction.getProvider() == PaymentProvider.PAYTR) {
            return gatewayResult.getPaidPrice().compareTo(transaction.getPaidPrice()) < 0;
        }
        return transaction.getPaidPrice().compareTo(gatewayResult.getPaidPrice()) != 0;
    }

    private PaymentProvider resolveProvider(PaymentProvider provider) {
        PaymentProvider resolved = provider == null ? paymentGatewayProperties.defaultProvider() : provider;
        if (resolved != PaymentProvider.PAYTR) {
            throw new PaymentProcessingException("UNSUPPORTED_PROVIDER", "Only PayTR is supported");
        }
        return PaymentProvider.PAYTR;
    }

    private void warnOnPaymentIdMismatch(PaymentTransaction transaction, String gatewayPaymentId) {
        if (gatewayPaymentId != null
                && transaction.getPaymentId() != null
                && !transaction.getPaymentId().equals(gatewayPaymentId)) {
            log.warn(
                    "PaymentId mismatch. transactionId={}, existing={}, gateway={}",
                    transaction.getId(),
                    transaction.getPaymentId(),
                    gatewayPaymentId
            );
        }
    }

    private void markFailure(PaymentTransaction transaction, String errorCode, String errorMessage) {
        transaction.setStatus(PaymentStatus.FAILURE);
        transaction.setErrorCode(errorCode);
        transaction.setErrorMessage(errorMessage);
        transaction.setUpdatedAt(LocalDateTime.now());
        paymentRepositoryPort.save(transaction);
    }

    private void publishPaymentEvent(PaymentTransaction transaction, boolean success) {
        if (transaction.getServiceName() == null || transaction.getServiceName().isBlank()) {
            log.warn("Payment event skipped because serviceName is missing. conversationId={}", transaction.getConversationId());
            return;
        }

        Map<String, Object> sourceMetadata = buildSourceMetadata(transaction);
        String eventType;
        if (transaction.getStatus() == PaymentStatus.REFUNDED) {
            eventType = "payment.refunded";
        } else if (transaction.getPaymentStyle() == PaymentStyle.SUBSCRIPTION) {
            eventType = success ? "payment.subscription.paid" : "payment.subscription.failed";
        } else {
            eventType = success ? "payment.success" : "payment.failed";
        }
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(deterministicEventId(transaction, eventType))
                .eventType(eventType)
                .occurredAt(Instant.now().toString())
                .paymentId(transaction.getPaymentId())
                .conversationId(transaction.getConversationId())
                .serviceName(transaction.getServiceName())
                .sourceReferenceId(transaction.getSourceReferenceId())
                .sourceMetadata(sourceMetadata)
                .purchaseId(metadataValue(sourceMetadata, "purchaseId", transaction.getSourceReferenceId()))
                .merchantId(metadataMerchantId(sourceMetadata))
                .packageId(metadataValue(sourceMetadata, "packageId", null))
                .packageCode(metadataValue(sourceMetadata, "packageCode", null))
                .paymentStyle(transaction.getPaymentStyle())
                .bankInstallmentCount(transaction.getBankInstallmentCount())
                .subscriptionId(transaction.getSubscriptionId())
                .billingCycleNumber(transaction.getBillingCycleNumber())
                .periodStart(resolvePeriodStart(transaction).toString())
                .periodEnd(resolvePeriodEnd(transaction, sourceMetadata).toString())
                .amount(transaction.getPaidPrice())
                .currency(transaction.getCurrency())
                .errorCode(transaction.getErrorCode())
                .failureReason(transaction.getErrorMessage())
                .build();

        paymentEventPublisherPort.publish(event, transaction.getServiceName(), success);
    }

    private String deterministicEventId(PaymentTransaction transaction, String eventType) {
        String value = transaction.getConversationId() + ":" + eventType;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private LocalDate resolvePeriodStart(PaymentTransaction transaction) {
        return transaction.getPeriodStart() == null ? LocalDate.now() : transaction.getPeriodStart();
    }

    private LocalDate resolvePeriodEnd(PaymentTransaction transaction, Map<String, Object> metadata) {
        if (transaction.getPeriodEnd() != null) {
            return transaction.getPeriodEnd();
        }
        String validityDays = metadataValue(metadata, "validityDays", "30");
        return resolvePeriodStart(transaction).plusDays(Long.parseLong(validityDays) - 1L);
    }

    private Integer metadataInteger(Map<String, Object> metadata, String key) {
        String value = metadataValue(metadata, key, null);
        return value == null ? null : Integer.valueOf(value);
    }

    private String metadataValue(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private String metadataMerchantId(Map<String, Object> metadata) {
        String merchantId = metadataValue(metadata, "merchantId", null);
        if (merchantId != null && !merchantId.isBlank()) {
            return merchantId;
        }
        return metadataValue(metadata, "userId", null);
    }

    private Map<String, Object> buildSourceMetadata(PaymentTransaction transaction) {
        Map<String, Object> sourceMetadata = new HashMap<>(
                sourceMetadataConverter.fromJson(transaction.getSourceMetadataJson())
        );
        if (transaction.getSourceReferenceId() != null) {
            sourceMetadata.put(SOURCE_METADATA_PURCHASE_ID_KEY, transaction.getSourceReferenceId());
        }
        return sourceMetadata;
    }

    private void publishPaymentSuccessNotification(CreatePaymentCommand command, PaymentTransaction transaction) {
        try {
            paymentNotificationPublisherPort.publishPaymentSuccess(command, transaction);
        } catch (Exception exception) {
            log.error(
                    "Payment success notification failed. conversationId={}",
                    transaction.getConversationId(),
                    exception
            );
        }
    }

    private void publishPaymentSuccessNotification(PaymentTransaction transaction) {
        try {
            paymentNotificationPublisherPort.publishPaymentSuccess(transaction);
        } catch (Exception exception) {
            log.error(
                    "Payment success notification failed. conversationId={}",
                    transaction.getConversationId(),
                    exception
            );
        }
    }

    private Integer resolveCheckoutInstallmentCount(InitializeCheckoutFormCommand command) {
        if (command.getEnabledInstallments() == null || command.getEnabledInstallments().isEmpty()) {
            return null;
        }
        return command.getEnabledInstallments().stream().max(Integer::compareTo).orElse(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveBuyerEmail(BuyerInfo buyer) {
        if (buyer == null || buyer.getEmail() == null) {
            return null;
        }
        String email = buyer.getEmail().trim();
        return email.isEmpty() ? null : email;
    }

    private String resolveBuyerName(BuyerInfo buyer) {
        if (buyer == null) {
            return null;
        }
        String name = buyer.getName() == null ? "" : buyer.getName().trim();
        String surname = buyer.getSurname() == null ? "" : buyer.getSurname().trim();
        String fullName = (name + " " + surname).trim();
        return fullName.isEmpty() ? null : fullName;
    }
}
