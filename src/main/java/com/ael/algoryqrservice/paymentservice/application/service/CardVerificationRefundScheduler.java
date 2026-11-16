package com.ael.algoryqrservice.paymentservice.application.service;

import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardVerificationRefundScheduler {

    private final PaymentService paymentService;
    private final SchedulerLock schedulerLock;

    @Scheduled(fixedDelayString = "${payment.verification-refund.fixed-delay:30000}")
    public void refundPendingVerificationCharges() {
        schedulerLock.runExclusively("card-verification-refund", Duration.ofMinutes(5), this::refundPendingVerificationChargesLocked);
    }

    void refundPendingVerificationChargesLocked() {
        try {
            paymentService.refundPendingVerificationCharges();
        } catch (Exception exception) {
            log.error("Deferred card-verification refund sweep failed", exception);
        }
    }
}
