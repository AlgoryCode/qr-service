package com.ael.algoryqrservice.paymentservice.application.service;

import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.exception.SubscriptionNotFoundException;
import com.ael.algoryqrservice.paymentservice.domain.model.AddressInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BasketItemInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BuyerInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentGatewayResult;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaytrMerchantOid;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentMethodEntity;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.SubscriptionEntity;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.SourceMetadataConverter;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.SubscriptionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int MANUAL_PAYMENT_GRACE_DAYS = 7;
    private static final List<String> RENEWAL_LINK_STATUSES = List.of(
            "ACTIVE",
            "PAST_DUE",
            "RETRY",
            "PENDING_3DS"
    );

    private final SubscriptionJpaRepository repository;
    private final PaymentMethodService paymentMethodService;
    private final JsonMapper jsonMapper;
    private final SourceMetadataConverter sourceMetadataConverter;

    @Transactional(transactionManager = "paymentTransactionManager")
    public SubscriptionResponse bootstrap(
            String accountId,
            String serviceName,
            String sourceReferenceId,
            String conversationId,
            BigDecimal amount,
            String currency,
            Integer billingIntervalMonths,
            Long paymentMethodId,
            LocalDateTime nextChargeAt,
            Map<String, Object> sourceMetadata,
            BuyerInfo buyer,
            AddressInfo shippingAddress,
            AddressInfo billingAddress
    ) {
        if (accountId == null || accountId.isBlank()) {
            throw new PaymentProcessingException("ACCOUNT_REQUIRED", "Account identifier is required");
        }
        if (paymentMethodId == null) {
            throw new PaymentProcessingException("CARD_REQUIRED", "Kayitli kart zorunludur");
        }
        if (nextChargeAt == null) {
            throw new PaymentProcessingException("NEXT_CHARGE_REQUIRED", "Ilk cekim tarihi zorunludur");
        }
        paymentMethodService.resolveToken(accountId, paymentMethodId);
        Optional<SubscriptionEntity> existing = repository
                .findFirstBySourceReferenceIdAndAccountIdAndStatusInOrderByCreatedAtDesc(
                        sourceReferenceId,
                        accountId,
                        RENEWAL_LINK_STATUSES
                );
        if (existing.isPresent()) {
            SubscriptionEntity subscription = existing.get();
            subscription.setPaymentMethodId(paymentMethodId);
            subscription.setAmount(amount);
            subscription.setCurrency(currency);
            subscription.setBillingIntervalMonths(resolveInterval(billingIntervalMonths));
            subscription.setNextChargeAt(nextChargeAt);
            subscription.setStatus("ACTIVE");
            subscription.setRetryCount(0);
            subscription.setLastError(null);
            subscription.setUpdatedAt(LocalDateTime.now());
            return toResponse(repository.save(subscription));
        }
        String seed = accountId + ":bootstrap:" + sourceReferenceId;
        String subscriptionId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        int intervalMonths = resolveInterval(billingIntervalMonths);
        CreatePaymentCommand template = CreatePaymentCommand.builder()
                .serviceName(serviceName)
                .sourceReferenceId(sourceReferenceId)
                .sourceMetadata(sourceMetadata)
                .currency(currency)
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingIntervalMonths(intervalMonths)
                .basketId(conversationId)
                .paymentGroup("PRODUCT")
                .buyer(buyer)
                .shippingAddress(shippingAddress)
                .billingAddress(billingAddress)
                .basketItems(List.of(BasketItemInfo.builder()
                        .id("subscription-renewal")
                        .name("Abonelik yenileme")
                        .category1("Billing")
                        .category2("Subscription")
                        .itemType("VIRTUAL")
                        .price(amount)
                        .build()))
                .build();
        LocalDateTime now = LocalDateTime.now();
        SubscriptionEntity saved = repository.save(SubscriptionEntity.builder()
                .id(subscriptionId)
                .accountId(accountId)
                .serviceName(serviceName)
                .sourceReferenceId(sourceReferenceId)
                .paymentMethodId(paymentMethodId)
                .initialConversationId(PaytrMerchantOid.sanitize(conversationId))
                .amount(amount)
                .currency(currency)
                .billingIntervalMonths(intervalMonths)
                .completedCycleCount(0)
                .status("ACTIVE")
                .nextChargeAt(nextChargeAt)
                .retryCount(0)
                .maxRetries(DEFAULT_MAX_RETRIES)
                .requestPayload(serializeRenewalTemplate(template))
                .createdAt(now)
                .updatedAt(now)
                .build());
        return toResponse(saved);
    }

    @Transactional(transactionManager = "paymentTransactionManager")
    public void initialize(CreatePaymentCommand command) {
        validateInitialCommand(command);
        String seed = command.getAccountId() + ":" + command.getConversationId();
        String subscriptionId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        command.setSubscriptionId(subscriptionId);
        command.setBillingCycleNumber(1);
        command.setPeriodStart(LocalDate.now());
        command.setPeriodEnd(LocalDate.now().plusMonths(resolveInterval(command)).minusDays(1));
        if (usesSavedPaymentMethod(command)) {
            if (command.getPaymentCard().getRegisterCard() == null) {
                command.getPaymentCard().setRegisterCard(0);
            }
        } else {
            command.getPaymentCard().setRegisterCard(1);
        }
        LocalDateTime now = LocalDateTime.now();
        repository.save(SubscriptionEntity.builder()
                .id(subscriptionId)
                .accountId(command.getAccountId())
                .serviceName(command.getServiceName())
                .sourceReferenceId(command.getSourceReferenceId())
                .paymentMethodId(command.getPaymentMethodId())
                .initialConversationId(command.getConversationId())
                .amount(command.getPaidPrice())
                .currency(command.getCurrency())
                .billingIntervalMonths(resolveInterval(command))
                .totalCycleCount(command.getSubscriptionCycleCount())
                .completedCycleCount(0)
                .status("PENDING_3DS")
                .retryCount(0)
                .maxRetries(DEFAULT_MAX_RETRIES)
                .requestPayload(serializeSafe(command))
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Transactional(transactionManager = "paymentTransactionManager")
    public void prepareCheckoutFormSubscription(InitializeCheckoutFormCommand command) {
        validateInitialCheckoutFormCommand(command);
        Optional<SubscriptionEntity> existing = findExistingForCheckout(command);
        if (existing.isPresent()) {
            SubscriptionEntity subscription = existing.get();
            command.setSubscriptionId(subscription.getId());
            command.setBillingCycleNumber(subscription.getCompletedCycleCount() + 1);
            command.setPeriodStart(LocalDate.now());
            command.setPeriodEnd(LocalDate.now()
                    .plusMonths(subscription.getBillingIntervalMonths())
                    .minusDays(1));
            return;
        }
        initializeFromCheckoutForm(command);
    }

    @Transactional(transactionManager = "paymentTransactionManager")
    public void initializeFromCheckoutForm(InitializeCheckoutFormCommand command) {
        validateInitialCheckoutFormCommand(command);
        String seed = command.getAccountId() + ":" + command.getConversationId();
        String subscriptionId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        int intervalMonths = resolveInterval(command.getBillingIntervalMonths());
        command.setSubscriptionId(subscriptionId);
        command.setBillingCycleNumber(1);
        command.setPeriodStart(LocalDate.now());
        command.setPeriodEnd(LocalDate.now().plusMonths(intervalMonths).minusDays(1));
        LocalDateTime now = LocalDateTime.now();
        repository.save(SubscriptionEntity.builder()
                .id(subscriptionId)
                .accountId(command.getAccountId())
                .serviceName(command.getServiceName())
                .sourceReferenceId(command.getSourceReferenceId())
                .initialConversationId(command.getConversationId())
                .amount(command.getPaidPrice())
                .currency(command.getCurrency())
                .billingIntervalMonths(intervalMonths)
                .totalCycleCount(command.getSubscriptionCycleCount())
                .completedCycleCount(0)
                .status("PENDING_3DS")
                .retryCount(0)
                .maxRetries(DEFAULT_MAX_RETRIES)
                .requestPayload(serializeRenewalTemplate(toRenewalTemplate(command, intervalMonths)))
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Transactional(transactionManager = "paymentTransactionManager")
    public void activate(PaymentTransaction transaction, PaymentGatewayResult result) {
        SubscriptionEntity subscription = resolveSubscription(transaction);
        if (subscription == null) {
            return;
        }
        if (subscription.getPaymentMethodId() == null
                && result.getCardUserKey() != null
                && result.getCardToken() != null) {
            PaymentMethodEntity method = paymentMethodService.saveGatewayToken(
                    subscription.getAccountId(),
                    "Subscription card",
                    result.getCardUserKey(),
                    result.getCardToken(),
                    result.getLastFourDigits()
            );
            subscription.setPaymentMethodId(method.getId());
        } else if (subscription.getPaymentMethodId() == null) {
            log.warn(
                    "Subscription activated without card tokens; auto-renew disabled until card is linked. "
                            + "conversationId={}, accountId={}",
                    transaction.getConversationId(),
                    subscription.getAccountId()
            );
        }

        LocalDateTime now = LocalDateTime.now();
        boolean initialPayment = subscription.getCompletedCycleCount() == 0
                || transaction.getConversationId() != null
                && transaction.getConversationId().equals(subscription.getInitialConversationId());
        if (initialPayment && subscription.getCompletedCycleCount() == 0) {
            subscription.setCompletedCycleCount(1);
        } else if (!initialPayment) {
            int cycle = transaction.getBillingCycleNumber() != null
                    ? transaction.getBillingCycleNumber()
                    : subscription.getCompletedCycleCount() + 1;
            subscription.setCompletedCycleCount(Math.max(subscription.getCompletedCycleCount(), cycle));
        }

        subscription.setRetryCount(0);
        subscription.setLastError(null);
        subscription.setStatus(isComplete(subscription) ? "COMPLETED" : "ACTIVE");
        subscription.setNextChargeAt(isComplete(subscription)
                ? null
                : now.plusMonths(subscription.getBillingIntervalMonths()));
        subscription.setUpdatedAt(now);
        repository.save(subscription);
    }

    @Transactional(transactionManager = "paymentTransactionManager")
    public void markManualPaymentRequired(SubscriptionEntity subscription, String reason) {
        LocalDateTime now = LocalDateTime.now();
        subscription.setStatus("PAST_DUE");
        subscription.setLastError(reason);
        subscription.setNextChargeAt(now.plusDays(MANUAL_PAYMENT_GRACE_DAYS));
        subscription.setUpdatedAt(now);
        repository.save(subscription);
    }

    public int manualPaymentGraceDays() {
        return MANUAL_PAYMENT_GRACE_DAYS;
    }

    @Transactional(readOnly = true, transactionManager = "paymentTransactionManager")
    public SubscriptionResponse get(String accountId, String id) {
        return toResponse(requireOwned(accountId, id));
    }

    @Transactional(transactionManager = "paymentTransactionManager")
    public SubscriptionResponse cancel(String accountId, String id) {
        SubscriptionEntity subscription = requireOwned(accountId, id);
        subscription.setStatus("CANCELLED");
        subscription.setNextChargeAt(null);
        subscription.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(subscription));
    }

    @Transactional(transactionManager = "paymentTransactionManager")
    public SubscriptionResponse changeCard(String accountId, String id, Long paymentMethodId) {
        SubscriptionEntity subscription = requireOwned(accountId, id);
        paymentMethodService.resolveToken(accountId, paymentMethodId);
        subscription.setPaymentMethodId(paymentMethodId);
        subscription.setRetryCount(0);
        subscription.setLastError(null);
        if ("PAST_DUE".equals(subscription.getStatus())) {
            subscription.setStatus("ACTIVE");
            subscription.setNextChargeAt(LocalDateTime.now());
        }
        subscription.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(subscription));
    }

    private Optional<SubscriptionEntity> findExistingForCheckout(InitializeCheckoutFormCommand command) {
        if (command.getSourceReferenceId() == null || command.getSourceReferenceId().isBlank()) {
            return Optional.empty();
        }
        if (command.getAccountId() == null || command.getAccountId().isBlank()) {
            return Optional.empty();
        }
        return repository.findFirstBySourceReferenceIdAndAccountIdAndStatusInOrderByCreatedAtDesc(
                command.getSourceReferenceId(),
                command.getAccountId(),
                RENEWAL_LINK_STATUSES
        );
    }

    private SubscriptionEntity resolveSubscription(PaymentTransaction transaction) {
        if (transaction.getConversationId() != null) {
            Optional<SubscriptionEntity> byInitial =
                    repository.findByInitialConversationId(transaction.getConversationId());
            if (byInitial.isPresent()) {
                return byInitial.get();
            }
        }
        if (transaction.getSubscriptionId() != null && !transaction.getSubscriptionId().isBlank()) {
            Optional<SubscriptionEntity> byId = repository.findById(transaction.getSubscriptionId());
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        if (transaction.getSourceReferenceId() != null
                && transaction.getAccountId() != null
                && !transaction.getSourceReferenceId().isBlank()) {
            return repository.findFirstBySourceReferenceIdAndAccountIdAndStatusInOrderByCreatedAtDesc(
                    transaction.getSourceReferenceId(),
                    transaction.getAccountId(),
                    RENEWAL_LINK_STATUSES
            ).orElse(null);
        }
        return null;
    }

    private void validateInitialCommand(CreatePaymentCommand command) {
        if (command.getAccountId() == null || command.getAccountId().isBlank()) {
            throw new PaymentProcessingException("ACCOUNT_REQUIRED", "Account identifier is required");
        }
        if (!hasCardForInitialSubscription(command)) {
            throw new PaymentProcessingException(
                    "CARD_REQUIRED",
                    "Initial subscription payment requires a card or a saved payment method"
            );
        }
        if (command.getSubscriptionCycleCount() != null && command.getSubscriptionCycleCount() < 2) {
            throw new PaymentProcessingException("INVALID_CYCLE_COUNT", "Subscription requires at least two cycles");
        }
    }

    private boolean hasCardForInitialSubscription(CreatePaymentCommand command) {
        if (command.getPaymentMethodId() != null) {
            return true;
        }
        if (command.getPaymentCard() == null) {
            return false;
        }
        if (isPresent(command.getPaymentCard().getCardNumber())) {
            return true;
        }
        return isPresent(command.getPaymentCard().getCardUserKey())
                && isPresent(command.getPaymentCard().getCardToken());
    }

    private boolean usesSavedPaymentMethod(CreatePaymentCommand command) {
        if (command.getPaymentMethodId() != null) {
            return true;
        }
        return command.getPaymentCard() != null
                && isPresent(command.getPaymentCard().getCardUserKey())
                && isPresent(command.getPaymentCard().getCardToken())
                && !isPresent(command.getPaymentCard().getCardNumber());
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private void validateInitialCheckoutFormCommand(InitializeCheckoutFormCommand command) {
        if (command.getAccountId() == null || command.getAccountId().isBlank()) {
            throw new PaymentProcessingException("ACCOUNT_REQUIRED", "Account identifier is required");
        }
        if (command.getSubscriptionCycleCount() != null && command.getSubscriptionCycleCount() < 2) {
            throw new PaymentProcessingException("INVALID_CYCLE_COUNT", "Subscription requires at least two cycles");
        }
    }

    private CreatePaymentCommand toRenewalTemplate(InitializeCheckoutFormCommand command, int intervalMonths) {
        return CreatePaymentCommand.builder()
                .serviceName(command.getServiceName())
                .sourceReferenceId(command.getSourceReferenceId())
                .sourceMetadata(command.getSourceMetadata())
                .currency(command.getCurrency())
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .subscriptionCycleCount(command.getSubscriptionCycleCount())
                .billingIntervalMonths(intervalMonths)
                .basketId(command.getBasketId())
                .paymentGroup(command.getPaymentGroup())
                .buyer(command.getBuyer())
                .shippingAddress(command.getShippingAddress())
                .billingAddress(command.getBillingAddress())
                .basketItems(command.getBasketItems())
                .build();
    }

    private String serializeRenewalTemplate(CreatePaymentCommand template) {
        try {
            String sanitizedMetadata = sourceMetadataConverter.toJson(template.getSourceMetadata());
            template.setSourceMetadata(sourceMetadataConverter.fromJson(sanitizedMetadata));
            return jsonMapper.writeValueAsString(template);
        } catch (Exception exception) {
            throw new IllegalStateException("Subscription request cannot be serialized safely", exception);
        }
    }

    private String serializeSafe(CreatePaymentCommand command) {
        try {
            String sanitizedMetadata = sourceMetadataConverter.toJson(command.getSourceMetadata());
            command.setSourceMetadata(sourceMetadataConverter.fromJson(sanitizedMetadata));
            String payload = jsonMapper.writeValueAsString(command);
            if (containsCardData(payload, command)) {
                throw new IllegalStateException("Subscription payload contains raw card data");
            }
            return payload;
        } catch (Exception exception) {
            throw new IllegalStateException("Subscription request cannot be serialized safely", exception);
        }
    }

    private boolean containsCardData(String payload, CreatePaymentCommand command) {
        return command.getPaymentCard() != null
                && ((command.getPaymentCard().getCardNumber() != null
                && payload.contains(command.getPaymentCard().getCardNumber()))
                || (command.getPaymentCard().getCvc() != null
                && payload.contains(command.getPaymentCard().getCvc())));
    }

    private int resolveInterval(CreatePaymentCommand command) {
        return resolveInterval(command.getBillingIntervalMonths());
    }

    private int resolveInterval(Integer billingIntervalMonths) {
        return billingIntervalMonths == null ? 1 : billingIntervalMonths;
    }

    private boolean isComplete(SubscriptionEntity subscription) {
        return subscription.getTotalCycleCount() != null
                && subscription.getCompletedCycleCount() >= subscription.getTotalCycleCount();
    }

    private SubscriptionEntity requireOwned(String accountId, String id) {
        return repository.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));
    }

    private SubscriptionResponse toResponse(SubscriptionEntity subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getPaymentMethodId(),
                subscription.getStatus(),
                subscription.getAmount(),
                subscription.getCurrency(),
                subscription.getCompletedCycleCount(),
                subscription.getTotalCycleCount(),
                subscription.getNextChargeAt()
        );
    }
}
