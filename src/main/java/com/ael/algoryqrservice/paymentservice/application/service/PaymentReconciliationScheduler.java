package com.ael.algoryqrservice.paymentservice.application.service;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentRepositoryPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentReconciliationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentService paymentService;
    private final PaymentReconciliationProperties properties;

    @Scheduled(fixedDelayString = "${payment.reconciliation.fixed-delay:900000}")
    public void reconcileStalePayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(properties.staleAfterMinutes());
        var stalePayments = paymentRepositoryPort.findStaleByStatus(
                PaymentStatus.INITIATED,
                threshold,
                properties.batchSize()
        );
        if (stalePayments.isEmpty()) {
            return;
        }
        log.info("Reconciling {} stale payment(s) older than {} minute(s).", stalePayments.size(), properties.staleAfterMinutes());
        for (PaymentTransaction transaction : stalePayments) {
            reconcile(transaction);
        }
    }

    private void reconcile(PaymentTransaction transaction) {
        try {
            paymentService.retrievePayment(transaction.getConversationId());
        } catch (Exception exception) {
            log.error(
                    "Payment reconciliation failed. conversationId={}",
                    transaction.getConversationId(),
                    exception
            );
        }
    }
}
