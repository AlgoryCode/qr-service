package com.ael.algoryqrservice.paymentservice.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardVerificationRefundScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${payment.verification-refund.fixed-delay:30000}")
    public void refundPendingVerificationCharges() {
        try {
            paymentService.refundPendingVerificationCharges();
        } catch (Exception exception) {
            log.error("Deferred card-verification refund sweep failed", exception);
        }
    }
}
