package com.ael.algoryqrservice.paymentservice.application.service;

import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCardInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCompletedEvent;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentEventPublisherPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.SubscriptionEntity;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository.SubscriptionJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRenewalScheduler {

    private static final int BATCH_SIZE = 20;
    private static final String NO_CARD_REASON = "Kayitli kart yok; manuel Checkout Form odemesi gerekli";

    private final SubscriptionJpaRepository repository;
    private final PaymentMethodService paymentMethodService;
    private final PaymentService paymentService;
    private final PaymentEventPublisherPort eventPublisher;
    private final SubscriptionService subscriptionService;
    private final JsonMapper jsonMapper;

    @Scheduled(fixedDelayString = "${payment.subscriptions.fixed-delay:60000}")
    @Transactional(transactionManager = "paymentTransactionManager")
    public void renewDueSubscriptions() {
        List<SubscriptionEntity> subscriptions = repository
                .findByStatusInAndNextChargeAtLessThanEqualOrderByNextChargeAt(
                        List.of("ACTIVE", "RETRY"),
                        LocalDateTime.now(),
                        PageRequest.of(0, BATCH_SIZE)
                );
        subscriptions.forEach(this::renew);
    }

    private void renew(SubscriptionEntity subscription) {
        List<PaymentMethodService.StoredPaymentMethod> methods;
        try {
            methods = paymentMethodService.resolveTokens(subscription.getAccountId());
        } catch (Exception exception) {
            scheduleRetry(subscription, exception);
            return;
        }
        if (methods.isEmpty()) {
            subscriptionService.markManualPaymentRequired(subscription, NO_CARD_REASON);
            publishPastDue(subscription);
            log.info(
                    "Subscription due without saved card; marked PAST_DUE for manual CF payment. subscriptionId={} graceDays={}",
                    subscription.getId(),
                    subscriptionService.manualPaymentGraceDays()
            );
            return;
        }
        int cycleNumber = subscription.getCompletedCycleCount() + 1;
        List<String> failures = new ArrayList<>();
        for (PaymentMethodService.StoredPaymentMethod method : methods) {
            try {
                CreatePaymentCommand command = buildRenewalCommand(subscription, cycleNumber, method);
                log.info("Subscription renewal card attempt. subscriptionId={} paymentMethodId={} lastFour={}",
                        subscription.getId(), method.id(), method.lastFourDigits());
                PaymentResultDto result = paymentService.createDirectPayment(command);
                if (result.getStatus() == PaymentStatus.SUCCESS) {
                    subscription.setPaymentMethodId(method.id());
                    subscription.setUpdatedAt(LocalDateTime.now());
                    repository.save(subscription);
                    log.info("Subscription renewal succeeded. subscriptionId={} paymentMethodId={}",
                            subscription.getId(), method.id());
                    return;
                }
                if (result.getStatus() == PaymentStatus.INITIATED) {
                    schedulePending(subscription);
                    return;
                }
                failures.add("method=" + method.id() + ": status=" + result.getStatus());
            } catch (Exception exception) {
                failures.add("method=" + method.id() + ": " + limit(exception.getMessage(), 300));
                log.warn("Subscription renewal card attempt failed. subscriptionId={} paymentMethodId={}",
                        subscription.getId(), method.id(), exception);
            }
        }
        scheduleRetry(subscription, new IllegalStateException(String.join("; ", failures)));
    }

    private CreatePaymentCommand buildRenewalCommand(
            SubscriptionEntity subscription,
            int cycleNumber,
            PaymentMethodService.StoredPaymentMethod method
    ) throws Exception {
        CreatePaymentCommand command = jsonMapper.readValue(subscription.getRequestPayload(), CreatePaymentCommand.class);
        command.setAccountId(subscription.getAccountId());
        command.setConversationId(subscription.getId() + "-cycle-" + cycleNumber + "-card-" + method.id());
        command.setSubscriptionId(subscription.getId());
        command.setBillingCycleNumber(cycleNumber);
        command.setPaymentMethodId(method.id());
        command.setPaymentCard(method.card());
        command.setPrice(subscription.getAmount());
        command.setPaidPrice(subscription.getAmount());
        command.setPeriodStart(LocalDate.now());
        command.setPeriodEnd(LocalDate.now().plusMonths(subscription.getBillingIntervalMonths()).minusDays(1));
        if (command.getBasketItems() != null && command.getBasketItems().size() == 1) {
            command.getBasketItems().getFirst().setPrice(subscription.getAmount());
        }
        return command;
    }

    private void schedulePending(SubscriptionEntity subscription) {
        subscription.setStatus("RETRY");
        subscription.setNextChargeAt(LocalDateTime.now().plusMinutes(15));
        subscription.setUpdatedAt(LocalDateTime.now());
        repository.save(subscription);
    }

    private void scheduleRetry(SubscriptionEntity subscription, Exception exception) {
        int retryCount = subscription.getRetryCount();
        if ("RETRY".equals(subscription.getStatus())) {
            retryCount++;
        }
        subscription.setRetryCount(retryCount);
        subscription.setStatus(retryCount >= subscription.getMaxRetries() ? "PAST_DUE" : "RETRY");
        subscription.setNextChargeAt(retryCount >= subscription.getMaxRetries()
                ? LocalDateTime.now().plusDays(subscriptionService.manualPaymentGraceDays())
                : LocalDateTime.now().plusDays(1));
        subscription.setLastError(limit(exception.getMessage(), 1000));
        subscription.setUpdatedAt(LocalDateTime.now());
        repository.save(subscription);
        if ("PAST_DUE".equals(subscription.getStatus())) {
            publishPastDue(subscription);
        }
        log.warn(
                "Subscription renewal failed. subscriptionId={} retryCount={}",
                subscription.getId(),
                retryCount
        );
    }

    private void publishPastDue(SubscriptionEntity subscription) {
        String eventSeed = subscription.getId() + ":past-due:" + subscription.getCompletedCycleCount();
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.nameUUIDFromBytes(eventSeed.getBytes(StandardCharsets.UTF_8)).toString())
                .eventType("payment.subscription.past_due")
                .occurredAt(Instant.now().toString())
                .conversationId(subscription.getInitialConversationId())
                .serviceName(subscription.getServiceName())
                .sourceReferenceId(subscription.getSourceReferenceId())
                .purchaseId(subscription.getSourceReferenceId())
                .sourceMetadata(Map.of(
                        "purchaseId", subscription.getSourceReferenceId() == null
                                ? ""
                                : subscription.getSourceReferenceId(),
                        "billingCycleNumber", subscription.getCompletedCycleCount() + 1
                ))
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .subscriptionId(subscription.getId())
                .billingCycleNumber(subscription.getCompletedCycleCount() + 1)
                .amount(subscription.getAmount())
                .currency(subscription.getCurrency())
                .failureReason(subscription.getLastError())
                .build();
        eventPublisher.publish(event, subscription.getServiceName(), false);
    }

    private boolean isComplete(SubscriptionEntity subscription) {
        return subscription.getTotalCycleCount() != null
                && subscription.getCompletedCycleCount() >= subscription.getTotalCycleCount();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
